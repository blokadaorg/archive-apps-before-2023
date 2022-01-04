//
//  This file is part of Blokada.
//
//  This Source Code Form is subject to the terms of the Mozilla Public
//  License, v. 2.0. If a copy of the MPL was not distributed with this
//  file, You can obtain one at https://mozilla.org/MPL/2.0/.
//
//  Copyright © 2020 Blocka AB. All rights reserved.
//
//  @author Karol Gusak
//

import Foundation
import Combine

class Config {

    static let shared = Config()

    private init() {
        oldiCloud.synchronizable = true
        listenToPublishers()
    }

    private lazy var accounts = Repos.accountRepo.accountHot
    private var cancellables = Set<AnyCancellable>()

    private func listenToPublishers() {
        accounts.sink(
            onValue: { it in
                let oldAccount = self._account.value
                self._account.value = it.account
                //self._keypair.value = it.keypair

                if oldAccount?.id != it.account.id {
                    self.onAccountIdChanged()
                }

                if oldAccount?.id != it.account.id || oldAccount?.active_until != it.account.active_until {
                    self.onAccountUpdated()
                    self.onConfigUpdated()
                }
            }
        )
        .store(in: &cancellables)
    }

    private let log = Logger("Config")
    private let decoder = initJsonDecoder()
    private let encoder = initJsonEncoder()

    // We persist config either on local storage, in the iCloud, or in RAM only
    private let localStorage = UserDefaults.standard
    private let iCloud = NSUbiquitousKeyValueStore()
    private let oldiCloud = KeychainSwift()

    private let _deviceToken = Atomic<DeviceToken?>(nil)
    private let _account = Atomic<Account?>(nil)
    private let _keypair = Atomic<Keypair?>(nil)
    private let _lease = Atomic<Lease?>(nil)
    private let _gateway = Atomic<Gateway?>(nil)

    // Called when any property has changed
    private var onConfigUpdated = {}

    // Called when account related propeties change
    private var onAccountUpdated = {}

    // Called when account ID (user changes account)
    private var onAccountIdChanged = {}

    // Called when device related properties change
    private var onDeviceUpdated = {}

    func setOnConfigUpdated(callback: @escaping () -> Void) {
        onMain {
            self.onConfigUpdated = callback
            callback()
        }
    }

    func setOnAccountUpdated(callback: @escaping () -> Void) {
        onMain {
            self.onAccountUpdated = callback
            callback()
        }
    }

    func setOnAccountIdChanged(callback: @escaping () -> Void) {
        onMain {
            self.onAccountIdChanged = callback
            callback()
        }
    }

    func setOnDeviceUpdated(callback: @escaping () -> Void) {
        onMain {
            self.onDeviceUpdated = callback
            callback()
        }
    }

    // Needs to be called once after app start
    func load() {
        //onBackground {
            self.log.v("Loading config")
            self._lease.value = self.loadLease()
            self._gateway.value = self.loadGateway()

            onMain {
                self.onConfigUpdated()
                self.onAccountUpdated()
            }
        //}
    }

    /**
           Thread safe getters
    */

    func hasAccount() -> Bool {
        return _account.value != nil && !_account.value!.id.isEmpty
    }

    func account() -> Account? {
        return _account.value
    }

    func accountId() -> AccountId {
        if let id = _account.value?.id, !id.isEmpty {
            return id
        } else {
            log.e("Accessing accountId before account is set")
            return ""
        }
    }

    func accountActive() -> Bool {
        return _account.value?.isActive() ?? false
    }

    func accountType() -> String {
        return _account.value?.type ?? ""
    }

    func hasKeys() -> Bool {
        return localStorage.string(forKey: "privateKey") != nil && localStorage.string(forKey: "publicKey") != nil
    }

    func privateKey() -> String {
        if let key = localStorage.string(forKey: "privateKey") {
            return key
        } else {
            log.e("Accessing privateKey before account is set")
            return ""
        }
    }

    func publicKey() -> String {
        if let key = localStorage.string(forKey: "publicKey") {
            return key
        } else {
            log.e("Accessing publicKey before account is set")
            return ""
        }
    }

    func vpnEnabled() -> Bool {
        return localStorage.bool(forKey: "vpnEnabled")
    }

    func hasLease() -> Bool {
        return _lease.value != nil
    }

    func lease() -> Lease? {
        return _lease.value
    }

    func leaseActive() -> Bool {
        return lease()?.isActive() ?? false
    }

    func hasGateway() -> Bool {
        return _gateway.value != nil
    }

    func gateway() -> Gateway? {
        return _gateway.value
    }

    func rateAppShown() -> Bool {
        return iCloud.bool(forKey: "rateAppShown")
    }

    func deviceToken() -> DeviceToken? {
        return _deviceToken.value
    }

    func firstRun() -> Bool {
        return localStorage.bool(forKey: "notFirstRun")
    }

    func deviceTag() -> String {
        return "TODO-device-tag"
    }

    func logRetention() -> String {
        return localStorage.string(forKey: "logRetention") ?? ""
    }

    func networkExtensionVersion() -> Int {
        return localStorage.integer(forKey: "networkExtensionVersion")
    }

    func expireSeen() -> Bool {
        return localStorage.bool(forKey: "expireSeen")
    }

    /**
            Thread safe setters
     */

    func setVpnEnabled(_ enabled: Bool) {
        if enabled != vpnEnabled() {
            localStorage.set(enabled, forKey: "vpnEnabled")

            onMain {
                self.onConfigUpdated()
            }
        }
    }

    func setLease(_ lease: Lease, _ gateway: Gateway) {
        _lease.value = lease
        persistLease(lease)
        _gateway.value = gateway
        persistGateway(gateway)

        onMain {
            self.onConfigUpdated()
        }
    }

    func clearLease() {
        _lease.value = nil
        localStorage.removeObject(forKey: "lease")
        _gateway.value = nil
        localStorage.removeObject(forKey: "gateway")
        setVpnEnabled(false)

        onMain {
            self.onConfigUpdated()
        }
    }

    func setDeviceToken(_ deviceToken: DeviceToken) {
        // this token does not need to be persisted as it gets pulled from apple API's
        _deviceToken.value = deviceToken

        onMain {
            self.onDeviceUpdated()
        }
    }

    func markRateAppShown() {
        // Persist in the cloud to not bother same user again
        iCloud.set(true, forKey: "rateAppShown")
        iCloud.synchronize()

        onMain {
            self.onConfigUpdated()
        }
    }

    func markFirstRun() {
        localStorage.set(true, forKey: "notFirstRun")
    }

    func setLogRetention(retention: String) {
        localStorage.set(retention, forKey: "logRetention")
    }

    func markNetworkExtensionVersion() {
        localStorage.set(6, forKey: "networkExtensionVersion")
    }

    func markExpireSeen(_ can: Bool) {
        localStorage.set(can, forKey: "expireSeen")
    }

    /**
           Private helper methods
    */

    private func loadLease() -> Lease? {
        let result = localStorage.string(forKey: "lease")
        guard let stringData = result else {
            return nil
        }

        let jsonData = stringData.data(using: .utf8)
        guard let json = jsonData else {
            log.e("Failed getting lease json")
            return nil
        }

        do {
            return try self.decoder.decode(Lease.self, from: json)
        } catch {
            log.e("Failed decoding lease json".cause(error))
            return nil
        }
    }

    private func persistLease(_ lease: Lease) {
        guard let body = lease.toJson() else {
            return log.e("Failed encoding lease json")
        }

        localStorage.set(body, forKey: "lease")
    }

    private func loadGateway() -> Gateway? {
        let result = localStorage.string(forKey: "gateway")
        guard let stringData = result else {
            return nil
        }

        let jsonData = stringData.data(using: .utf8)
        guard let json = jsonData else {
            log.e("Failed getting gateway json")
            return nil
        }

        do {
            return try self.decoder.decode(Gateway.self, from: json)
        } catch {
            log.e("Failed decoding gateway json".cause(error))
            return nil
        }
    }

    private func persistGateway(_ gateway: Gateway) {
        guard let body = gateway.toJson() else {
            return log.e("Failed encoding gateway json")
        }

        localStorage.set(body, forKey: "gateway")
    }
}