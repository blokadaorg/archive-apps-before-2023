//
//  This file is part of Blokada.
//
//  This Source Code Form is subject to the terms of the Mozilla Public
//  License, v. 2.0. If a copy of the MPL was not distributed with this
//  file, You can obtain one at https://mozilla.org/MPL/2.0/.
//
//  Copyright © 2022 Blocka AB. All rights reserved.
//
//  @author Kar
//

import Foundation
import Combine
import NetworkExtension

class WgService: NetxServiceIn {

    var wgStateHot: AnyPublisher<NetworkStatus, Never> {
        writeWgState.compactMap { $0 }.eraseToAnyPublisher()
    }

    private var permsHot: AnyPublisher<Granted, Never> {
        writePerms.compactMap { $0 }.removeDuplicates().eraseToAnyPublisher()
    }

    fileprivate let writeWgState = CurrentValueSubject<NetworkStatus?, Never>(nil)
    fileprivate let writePerms = CurrentValueSubject<Granted?, Never>(nil)

    fileprivate let checkPermsT = SimpleTasker<Ignored>("checkPerms")

    private let bgQueue = DispatchQueue(label: "WgServiceBgQueue")
    private var cancellables = Set<AnyCancellable>()

    var tunnelsManager = Atomic<TunnelsManager?>(nil)
    var tunnelsTracker: TunnelsTracker?

    func start() {
        initTunnelsManager()
        onPermsGranted_startMonitoringWg()
        onCheckPerms()
        checkPerms()
        self.writeWgState.send(NetworkStatus.disconnected())
    }

    private func initTunnelsManager() {
        TunnelsManager.create { [weak self] result in
            guard let self = self else { return }

            switch result {
            case .failure(let error):
                BlockaLogger.e("WgService", "Failed to init TunnelsManager".cause(error))
                ErrorPresenter.showErrorAlert(error: error, from: nil)
            case .success(let tunnelsManager):
                let tunnelsTracker = TunnelsTracker(tunnelsManager: tunnelsManager)

                tunnelsTracker.onTunnelState = { status in
                    self.writeWgState.send(status)
                }

                self.tunnelsManager = Atomic(tunnelsManager)
                self.tunnelsTracker = tunnelsTracker

                self.workaroundFirstConfigProblem(manager: tunnelsManager)
            }
        }
    }
    // This is a hacky workaround to fix a problem when for some reason first config
    // delivered to wg-ios leads to leaking (IP is not hidden). On subsequent config
    // changes everything works fine. This call is used to provide a temporary first
    // config, which causes wg-ios to reload on the second (actual) config.
    private func workaroundFirstConfigProblem(manager: TunnelsManager) {
        // Dont apply when no prems yet
        if manager.numberOfTunnels() == 0 {
            return
        }

        let key = PrivateKey()
        var interface = InterfaceConfiguration(privateKey: key)
        var peer = PeerConfiguration(publicKey: key.publicKey)
        let tunnelConfiguration = TunnelConfiguration(name: "Blokada+ (...)", interface: interface, peers: [peer])
        let container = manager.tunnel(at: 0)

        manager.modify(tunnel: container, tunnelConfiguration: tunnelConfiguration, onDemandOption: .off) { error -> Void in
            guard error == nil else {
                return BlockaLogger.e("WgService", "Could not do workaround".cause(error))
            }

            BlockaLogger.e("WgService", "Applied workaround config")
        }
    }

    func setConfig(_ config: NetxConfig) -> AnyPublisher<Ignored, Error> {
        return getManager()
        // Skip if no perms yet or wrong config
        .tryMap { manager in
            if manager.numberOfTunnels() == 0 {
                throw "No VPN perms yet"
            }
            if config.lease.vip4.isEmpty {
                throw "No vip4 is set"
            }
            return manager
        }
        // Modify tunnel configuration
        .flatMap { manager in
            let dns = self.getUserDnsIp(config.deviceTag)
            BlockaLogger.v("WgWgService", "setConfig: gateway: \(config.gateway.niceName()), tag: \(config.deviceTag), dns: \(dns)")

            var interface = InterfaceConfiguration(privateKey: PrivateKey(base64Key: config.privateKey)!)
            interface.addresses = [
                IPAddressRange(from: "\(config.lease.vip4)/32")!,
                IPAddressRange(from: "\(config.lease.vip6)/64")!,
            ]
            interface.dns = [DNSServer(from: dns)!]

            var peer = PeerConfiguration(publicKey: PublicKey(base64Key: config.gateway.public_key)!)
            peer.endpoint = Endpoint(from: "\(config.gateway.ipv4):51820")
            peer.allowedIPs = [
                IPAddressRange(from: "0.0.0.0/0")!,
                IPAddressRange(from: "::/0")!,
            ]

            let tunnelConfiguration = TunnelConfiguration(name: "Blokada+ (\(config.gateway.niceName()))", interface: interface, peers: [peer])

            let container = manager.tunnel(at: 0)
            return Future<TunnelsManager, Error> { promise in
                manager.modify(tunnel: container, tunnelConfiguration: tunnelConfiguration, onDemandOption: .off) { error -> Void in
                    guard error == nil else {
                        return promise(.failure("setConfig: could not modify tunnel".cause(error)))
                    }

                    return promise(.success(manager))
                }
            }
//            .map { manager in
//                BlockaLogger.w("WgService", "Hack restart")
//                container.status = .restarting
//                (container.tunnelProvider.connection as? NETunnelProviderSession)?.stopTunnel()
//                return manager
//            }
            .eraseToAnyPublisher()
        }
        .map { _ in true }
        .eraseToAnyPublisher()
    }

    func startVpn() -> AnyPublisher<Ignored, Error> {
        return startVpnInternal()
        .tryCatch { error in
            // A delayed retry (total 3 attemps that wait up to 5 sec)
            return self.startVpnInternal()
            .retry(1)
        }
        .eraseToAnyPublisher()
    }

    private func startVpnInternal() -> AnyPublisher<Ignored, Error> {
        return wgStateHot.filter { !$0.inProgress }.first()
        .flatMap { state -> AnyPublisher<Ignored, Error> in
            // VPN already started, ignore
            if state.active {
                return Just(true).setFailureType(to: Error.self).eraseToAnyPublisher()
            }

            return self.getManager()
            .tryMap { manager in
                if manager.numberOfTunnels() == 0 {
                    throw "No VPN perms yet"
                }
                return manager
            }
            // Actually start VPN
            .tryMap { manager -> Ignored in
                BlockaLogger.v("WgService", "Starting tunnel")
                manager.startActivation(of: manager.tunnel(at: 0))
                return true
            }
            .delay(for: 0.5, scheduler: self.bgQueue)
            //.map { _ in self.queryWgState() }
            .map { _ in true }
            // Wait for completion or timeout
            .flatMap { _ in
                Publishers.Merge(
                    // Wait until active state is reported
                    self.wgStateHot.filter { $0.active }.first().tryMap { _ in true }
                    .eraseToAnyPublisher(),

                    // Also make a timeout
                    Just(true)
                    .delay(for: 3.0, scheduler: self.bgQueue)
                    .flatMap { _ in self.wgStateHot.first() }
                    .tryMap { state -> Ignored in
                        if !state.active {
                            throw "timeout"
                        }
                        return true
                    }
                    .eraseToAnyPublisher()
                )
                .first()
                .eraseToAnyPublisher()
            }
            .eraseToAnyPublisher()
        }
        .eraseToAnyPublisher()
    }

    func stopVpn() -> AnyPublisher<Ignored, Error> {
        return wgStateHot.filter { !$0.inProgress }.first()
        .flatMap { state -> AnyPublisher<Ignored, Error> in
            // VPN already stopped, ignore
            if !state.active {
                return Just(true).setFailureType(to: Error.self).eraseToAnyPublisher()
            }

            return self.getManager()
            // Actually stop VPN
            .tryMap { manager -> Ignored in
                BlockaLogger.v("WgService", "Stopping tunnel")
                manager.startDeactivation(of: manager.tunnel(at: 0))
                return true
            }
            // Wait for completion or timeout
            .flatMap { _ in
                Publishers.Merge(
                    // Wait until inactive state is reported
                    self.wgStateHot.filter { !$0.active }.first().tryMap { _ in true }
                    .eraseToAnyPublisher(),

                    // Also make a timeout
                    Just(true)
                    .delay(for: 15.0, scheduler: self.bgQueue)
                    .flatMap { _ in self.wgStateHot.first() }
                    .tryMap { state -> Ignored in
                        if state.active {
                            //throw "stopvpn timeout"
                            // Somethings up with the wg state callback
                            self.writeWgState.send(NetworkStatus.disconnected())
                        }
                        return true
                    }
                    .eraseToAnyPublisher()
                )
                .first()
                .eraseToAnyPublisher()
            }
            // As a last resort re-check current state
            // It seems NETX can timeout at weird situations but it actually stops
            //.map { _ in self.queryWgState() }
            .map { _ in true }
            .eraseToAnyPublisher()
        }
        .eraseToAnyPublisher()
    }

    func createVpnProfile() -> AnyPublisher<Ignored, Error> {
        BlockaLogger.v("WgService", "Creating new VPN profile")
        return getManager()
        // Remove existing tunnel if any
        .flatMap { manager -> AnyPublisher<TunnelsManager, Error> in
            if manager.numberOfTunnels() > 0 {
                return Future<TunnelsManager, Error> { promise in
                    manager.remove(tunnel: manager.tunnel(at: 0)) { error -> Void in
                        if let error = error {
                            return promise(.failure("createVpnProfile: could not remove".cause(error)))
                        }
                        
                        return promise(.success(manager))
                    }
                }
                .eraseToAnyPublisher()
            } else {
                return Just(manager).setFailureType(to: Error.self).eraseToAnyPublisher()
            }
        }
        // Add new empty tunnel configuration (will be replaced before starting)
        .flatMap { manager -> AnyPublisher<TunnelsManager, Error> in
            var interface = InterfaceConfiguration(privateKey: PrivateKey())
            interface.addresses = [
                IPAddressRange(from: "0.0.0.0/32")!,
            ]
            //interface.dns = [DNSServer(from: dns)!]

            var peer = PeerConfiguration(publicKey: PrivateKey().publicKey)
            peer.endpoint = Endpoint(from: "0.0.0.0:51820")
            peer.allowedIPs = [
                IPAddressRange(from: "0.0.0.0/0")!,
            ]

            let tunnelConfiguration = TunnelConfiguration(name: "Blokada", interface: interface, peers: [peer])

            return Future<TunnelsManager, Error> { promise in
                manager.add(tunnelConfiguration: tunnelConfiguration) { result -> Void in
                    switch result {
                    case .success(_):
                        return promise(.success(manager))
                    case .failure(let error):
                        return promise(.failure("createVpnProfile: could not add".cause(error)))
                    }
                }
            }
            .eraseToAnyPublisher()
        }
        .map { _ in true }
        .eraseToAnyPublisher()
    }

//    // Make a request outside of the tunnel while tunnel is established.
//    // It is used while VPN is on, in order to be able to do requests even
//    // if tunnel is cut out (for example because it expired).
//    func makeProtectedRequest(url: String, method: String, body: String) -> AnyPublisher<String, Error> {
//        let request = [
//            NetworkCommand.request.rawValue, url, method, ":body:", body
//        ].joined(separator: " ")
//        return sendNetxMessage(msg: request)
//    }

    // Will create a NETX timer that is not killed in bg. No param means unpause.
    func changePause(until: Date? = nil) -> AnyPublisher<Ignored, Error> {
//        return Just(until ?? Date())
//        .tryMap { until in Int(until.timeIntervalSince(Date())) }
//        .map { seconds in
//            [ NetworkCommand.pause.rawValue, String(seconds) ]
//            .joined(separator: " ")
//        }
//        .flatMap { request in self.sendWgMessage(msg: request) }
//        .map { _ in self.queryWgState() }
//        .map { _ in true }
        return Just(false)
        .setFailureType(to: Error.self)
        .eraseToAnyPublisher()
    }

    func getStatePublisher() -> AnyPublisher<NetworkStatus, Never> {
        return wgStateHot
    }
    
    func getPermsPublisher() -> AnyPublisher<Granted, Never> {
        return permsHot
    }

    func checkPerms() {
        checkPermsT.send()
    }

    func onCheckPerms() {
        checkPermsT.setTask { _ in Just(true)
            .flatMap { _ in self.getManager() }
            .map { manager in
                return manager.numberOfTunnels() > 0
            }
            .map { it in self.writePerms.send(it) }
            .map { _ in true }
            .eraseToAnyPublisher()
        }
    }

    // Creates initial configuration (when user grants VPN permissions).
    // Must be overwritten with setConfig() before calling startVpn().
    private func setInitialConfig(_ manager: NETunnelProviderManager) -> AnyPublisher<Ignored, Error> {
        return Just(manager)
        .tryMap { manager -> NETunnelProviderManager in
            let protoConfig = NETunnelProviderProtocol()
            protoConfig.providerBundleIdentifier = "net.blocka.app.engine"
            protoConfig.serverAddress = "127.0.0.1"
            protoConfig.username = ""
            protoConfig.providerConfiguration = [:]
            manager.protocolConfiguration = protoConfig
            manager.localizedDescription = "BLOKADA"
            manager.isEnabled = true
            return manager
        }
        .flatMap { manager -> AnyPublisher<Ignored, Error> in
            return Future<Ignored, Error> { promise in
                manager.saveToPreferences() { error -> Void in
                    if let error = error {
                        return promise(.failure("setInitialConfig".cause(error)))
                    }

                    return promise(.success(true))
                }
            }
            .eraseToAnyPublisher()
        }
        .eraseToAnyPublisher()
    }

    private func onPermsGranted_startMonitoringWg() {
        permsHot
        .sink(onValue: { granted in
            if granted {
                // Calling startMonitoringWg several times is ok
                //self.startMonitoringWg()
            } else {
                // Emit disconnected (fresh app install, or perms rejected)
                BlockaLogger.v("WgService", "No perms, emitting disconnected")
                self.writeWgState.send(NetworkStatus.disconnected())
            }
            
        })
        .store(in: &cancellables)
    }
    
    func makeProtectedRequest(url: String, method: String, body: String) -> AnyPublisher<String, Error> {
        let request = [
            NetworkCommand.request.rawValue, url, method, ":body:", body
        ].joined(separator: " ")
        return sendWgMessage(msg: request)
    }

    private func sendWgMessage(msg: String) -> AnyPublisher<String, Error> {
        // Wait until NETX is initialized before any requests
        return writeWgState.first()
        .flatMap { _ in self.getManager() }
        // Actually get the iOS manager object
        .tryMap { manager -> NETunnelProviderManager in
            return manager.tunnel(at: 0).tunnelProvider
        }
        // Prepare connection to send message through
        .tryMap { manager -> (Data, NETunnelProviderSession) in
            let data = msg.data(using: String.Encoding.utf8)!
            let connection = manager.connection as! NETunnelProviderSession
            return (data, connection)
        }
        // Send the message and wait for response or error or timeout
        .flatMap { it -> AnyPublisher<String, Error> in
            let (data, connection) = it
            return Publishers.Merge(
                // Actual request to NETX
                Future<String, Error> { promise in
                    do {
                        try connection.sendProviderMessage(data) { reply in
                            guard let reply = reply else {
                                return promise(.failure("sendWgMessage: got a nil reply back for command".cause(msg)))
                            }

                            let data = String.init(data: reply, encoding: String.Encoding.utf8)!
                            if (data.starts(with: "error: code: ")) {
                                let code = Int(data.components(separatedBy: "error: code: ")[1])!
                                return promise(.failure(NetworkError.http(code)))
                            } else if (data.starts(with: "error: ")) {
                                return promise(.failure(data))
                            } else {
                                return promise(.success(data))
                            }
                        }
                    } catch {
                        return promise(.failure("sendWgMessage: sending message failed".cause(error)))
                    }
                }
                .eraseToAnyPublisher(),

                // Also make a timeout
                Just(true)
                .delay(for: 5.0, scheduler: self.bgQueue)
                .tryMap { state -> String in
                    throw "WG message timeout"
                }
                .eraseToAnyPublisher()
            )
            .first() // Whatever comes first: a response, or the timeout throwing
            .eraseToAnyPublisher()
        }
        .eraseToAnyPublisher()
    }

//    private func startMonitoringWg() {
//        BlockaLogger.v("WgService", "startMonitoringWg")
//
//        if let observer = wgStateObserver.value {
//            NotificationCenter.default.removeObserver(observer)
//        }
//
//        getManager()
//        .tryMap { manager -> NETunnelProviderSession in
//            guard let connection = manager.connection as? NETunnelProviderSession else {
//                throw "startMonitoringWg: no connection in manager"
//            }
//            return connection
//        }
//        .sink(
//            onValue: { connection in
//                self.wgStateObserver.value = NotificationCenter.default.addObserver(
//                    forName: NSNotification.Name.NEVPNStatusDidChange,
//                    object: connection,
//                    queue: OperationQueue.main,
//                    using: self.wgStateListener
//                )
//
//                // Check the current state as we'll only get state changes
//                self.queryWgState()
//            },
//            onFailure: { err in
//                BlockaLogger.e("WgService", "Could not start montioring".cause(err))
//            }
//        )
//        .store(in: &cancellables)
//    }

//    private func onQueryWgState() {
//        queryWgStateT.setTask { _ in
//            self.getManager()
//            // Get the status information from the manager connection
//            .tryMap { manager -> NetworkStatus in
//                guard let connection = manager.connection as? NETunnelProviderSession else {
//                    throw "queryWgState: no connection in manager"
//                }
//
//                var gatewayId: String? = nil
//                if let server = (manager.protocolConfiguration as? NETunnelProviderProtocol)?.providerConfiguration?["gatewayId"] {
//                    gatewayId = server as? String
//                }
//
//                let active = connection.status == NEVPNStatus.connected && manager.isEnabled
//                let inProgress = [
//                    NEVPNStatus.connecting, NEVPNStatus.disconnecting, NEVPNStatus.reasserting
//                ].contains(connection.status)
//
//                return NetworkStatus(
//                    active: active, inProgress: inProgress,
//                    gatewayId: gatewayId, pauseSeconds: 0
//                )
//            }
//            // Get the pause information from the NETX itself (ignore error)
//            .flatMap { status  -> AnyPublisher<(NetworkStatus, String), Error> in
//                BlockaLogger.v("WgService", "Query state: before report")
//                return Publishers.CombineLatest(
//                    Just(status).setFailureType(to: Error.self).eraseToAnyPublisher(),
//                    self.sendWgMessage(msg: NetworkCommand.report.rawValue)
//                    // First retry, maybe we queried too soon
//                    .tryCatch { err in
//                        self.sendWgMessage(msg: NetworkCommand.report.rawValue)
//                        .delay(for: 3.0, scheduler: self.bgQueue)
//                        .retry(1)
//                    }
//                    // Then ignore error, it's not the crucial part of the query
//                    //.tryCatch { err in Just("0") }
//                )
//                .eraseToAnyPublisher()
//            }
//            // Put it all together
//            .tryMap { it -> NetworkStatus in
//                BlockaLogger.v("WgService", "Query state: after report")
//                let (status, response) = it
//                var pauseSeconds = 0
//                if response != "off" {
//                    pauseSeconds = Int(response) ?? 0
//                }
//                return NetworkStatus(
//                    active: status.active, inProgress: status.inProgress,
//                    gatewayId: status.gatewayId, pauseSeconds: pauseSeconds
//                )
//            }
//            .tryMap { it in self.writeWgState.send(it) }
//            .tryMap { _ in true }
//            .tryCatch { err -> AnyPublisher<Ignored, Error> in
//                BlockaLogger.e(
//                    "WgService",
//                    "queryWgState: could not get status info".cause(err)
//                )
//
//                // Re-load the manager (maybe VPN profile removed)
//                self.manager = Atomic(nil)
//
//                if let e = err as? CommonError, e == .vpnNoPermissions {
//                    BlockaLogger.w("WgService", "marking VPN as disabled")
//                    self.writeWgState.send(NetworkStatus.disconnected())
//                }
//
//                throw err
//            }
//            .eraseToAnyPublisher()
//        }
//    }

    // Returns a manager ref, or errors out if not available
    private func getManager() -> AnyPublisher<TunnelsManager, Error> {
        if let m = tunnelsManager.value {
            return Just(m).setFailureType(to: Error.self).eraseToAnyPublisher()
        }

        return Fail(error: "No TunnelsManager available")
        .eraseToAnyPublisher()
    }

    private func getUserDnsIp(_ tag: String) -> String {
        if tag.count == 6 {
            // 6 chars old tag
            return "2001:678:e34:1d::\(tag.prefix(2)):\(tag.suffix(4))"
        } else {
            // 11 chars new tag
            return "2001:678:e34:1d::\(tag.prefix(3)):\(tag.dropFirst(3).prefix(4)):\(tag.suffix(4))"
        }
    }

}

