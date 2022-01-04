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
import UIKit
import Combine

class HomeViewModel: ObservableObject {

    private let log = Logger("Home")

    private let appRepo = Repos.appRepo
    private let cloudRepo = Repos.cloudRepo
    private let permsRepo = Repos.permsRepo

    private let errorsHot = Repos.processingRepo.errorsHot
    private let blockedCounterHot = Repos.statsRepo.blockedHot

    private var cancellables = Set<AnyCancellable>()

    @Published var showSplash = true

    @Published var appState: AppState = .Deactivated
    @Published var vpnEnabled: Bool = false
    @Published var working: Bool = false

    @Published var dnsPermsGranted: Bool = false
    @Published var vpnPermsGranted: Bool = false
    @Published var notificationPermsGranted: Bool = false
    
    @Published var accountActive = false
    @Published var accountType = ""

    @Published var showError: Bool = false
    var errorHeader: String? = nil
    var error: String? = nil {
        didSet {
            if error != nil {
                showError = true
            } else {
                showError = false
                errorHeader = nil
//                if showExpired {
//                    showExpired = false
//                    expiredAlertShown = false
//                }
            }
        }
    }

//    private var showExpired: Bool = false {
//        didSet {
//            if showExpired {
//                errorHeader = L10n.alertVpnExpiredHeader
//                error = L10n.errorVpnExpired
//                showError = true
//            } else {
//                self.turnVpnOffAfterExpired()
//                self.notification.clearNotification()
//            }
//        }
//    }
    var expiredAlertShown: Bool = false

    var onAccountExpired = {}


    @Published var timerSeconds: Int = 0
    private var timerBackgroundTask: UIBackgroundTaskIdentifier = .invalid

    @Published var selectedGateway: Gateway? = nil

    var hasSelectedLocation : Bool {
        return selectedGateway != nil
    }
//
    var location: String {
        return "None"
//        return selectedGateway?.niceName() ?? "None"
    }
//
    var hasLease: Bool {
        return false
//        return accountActive && Config.shared.leaseActive()
    }

    @Published var blockedCounter: Int = 0

    var encryptionLevel: Int {
        if working {
            return 1
        } else if appState == .Deactivated {
            return 1
        } else if !vpnEnabled {
            return 2
        } else {
            return 3
        }
    }

    
    init() {
//        sharedActions.changeGateway = switchGateway
        Config.shared.setOnConfigUpdated {
            onMain {
                self.syncUiWithConfig()
                self.syncUiWithTunnel(done: { _, _ in } )
            }
        }

//        network.onStatusChanged = { status in
//            onMain {
//                self.working = status.inProgress
//                //self.mainSwitch = self.working ? self.mainSwitch : status.active
//                Config.shared.setVpnEnabled(status.hasGateway())
//                self.syncUiWithConfig()
//            }
//        }
//        expiration.setOnExpired {
//            self.showExpiredAlert()
//            self.stopTimer()
//        }

        onMajorErrorDisplayDialog()
        onAppStateChanged()
        onWorking()
        onAccountTypeChanged()
        onStatsChanged()
        onPermsRepoChanged()
        onPauseUpdateTimer()
    }

    private func onMajorErrorDisplayDialog() {
        errorsHot.filter { it in it.major }
        .map { it in "Error:  \(it)" }
        .receive(on: RunLoop.main)
        .sink(onValue: { it in self.error = it })
        .store(in: &cancellables)
    }

    private func onAppStateChanged() {
        appRepo.appStateHot
        .receive(on: RunLoop.main)
        .sink(onValue: { it in
            self.appState = it
        })
        .store(in: &cancellables)
    }

    private func onWorking() {
        appRepo.workingHot
        .receive(on: RunLoop.main)
        .sink(onValue: { it in
            self.working = it
        })
        .store(in: &cancellables)
    }

    private func onAccountTypeChanged() {
        appRepo.accountType
        .receive(on: RunLoop.main)
        .sink(onValue: { it in
            self.accountType = it.toString()
            self.accountActive = it.isActive()
        })
        .store(in: &cancellables)
    }

    private func onStatsChanged() {
        blockedCounterHot
        .receive(on: RunLoop.main)
        .sink(onValue: { it in
            self.blockedCounter = it
        })
        .store(in: &cancellables)
    }

    private func onPermsRepoChanged() {
        permsRepo.dnsProfilePerms
        .receive(on: RunLoop.main)
        .sink(onValue: { it in
            self.dnsPermsGranted = it
        })
        .store(in: &cancellables)

        permsRepo.vpnProfilePerms
        .receive(on: RunLoop.main)
        .sink(onValue: { it in
            self.vpnPermsGranted = it
        })
        .store(in: &cancellables)

        permsRepo.notificationPerms
        .receive(on: RunLoop.main)
        .sink(onValue: { it in
            self.notificationPermsGranted = it
        })
        .store(in: &cancellables)
    }

    private func onPauseUpdateTimer() {
        appRepo.pausedUntilHot
        .receive(on: RunLoop.main)
        .sink(onValue: { it in
            // Display the countdown only for short timers.
            // Long timer is the "backup" timer to remind user to unpause the app if
            // they "turn it off" in Cloud mode (the app cannot be fully turned of in
            // this mode, as we can't programatically deactivate the DNS profile.)
            if let it = it, let seconds = Calendar.current.dateComponents([.second], from: Date(), to: it).second, seconds <= 60 * 30 {
                return self.startTimer(seconds: seconds)
            }

            self.stopTimer()
        })
        .store(in: &cancellables)
    }

    func pause(seconds: Int?) {
        let until = seconds != nil ? getDateInTheFuture(seconds: seconds!) : nil
        appRepo.pauseApp(until: until)
        .receive(on: RunLoop.main)
        .sink(onFailure: { err in self.error = "\(err)" })
        .store(in: &cancellables)
    }

    func unpause() {
        appRepo.unpauseApp()
        .receive(on: RunLoop.main)
        .sink(onFailure: { err in self.error = "\(err)" })
        .store(in: &cancellables)
    }

    func startTimer(seconds: Int) {
        self.log.v("startTimer: starting pause")
        self.timerSeconds = seconds
        Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { timer in
            self.timerSeconds = self.timerSeconds - 1
            if self.timerSeconds <= 0 {
                self.stopTimer()
                timer.invalidate()
            }

            if var timeLeft = Int(exactly: UIApplication.shared.backgroundTimeRemaining.rounded()) {
                // It starts from 29 usually, thats why +1
                timeLeft += 1
                if timeLeft % 10 == 0 {
                    self.log.v("Background time left: \(timeLeft)s")
                }
            }
        }
    }

    var isPaused: Bool {
        return self.timerSeconds != 0
    }

    func stopTimer() {
        if self.timerSeconds >= 0 {
            self.timerSeconds = 0
            self.log.v("stopTimer: stopping pause")
        } else {
            self.timerSeconds = 0
        }
        endBackgroundTask()
    }

    func endBackgroundTask() {
        if timerBackgroundTask != .invalid {
        self.log.v("Background task ended")
            UIApplication.shared.endBackgroundTask(timerBackgroundTask)
            timerBackgroundTask = .invalid
        }
    }

    func finishSetup() {
        self.permsRepo.askForAllMissingPermissions()
        .sink()
        .store(in: &cancellables)
    }

//    private func start(_ done: @escaping Callback<Void>) {
//        self.log.v("Start")
//        self.error = nil
//        self.working = true
//        self.syncUiWithConfig()
//
//        onBackground {
//            self.syncUiWithTunnel { error, status in onMain {
//                guard error == nil else {
//                    if error is CommonError && (error as! CommonError) == CommonError.vpnNoPermissions {
//                       return onMain {
//                           self.log.v("No VPN profile")
//
//                           // Do not treat this as an error
//                           return self.afterStart(done)
//                       }
//                   }
//
//                    self.handleError(CommonError.failedTunnel, cause: error)
//                    return done(error, nil)
//                }
//
//                return self.afterStart(done)
////                if !Config.shared.hasAccount() {
////                    // New user scenario: just create account
////                    self.sharedActions.newUser { error, _ in
////                        guard error == nil else {
////                            self.handleError(CommonError.failedFetchingData, cause: error)
////                            return done(error, nil)
////                        }
////
////                        return self.afterStart(done)
////                    }
////                } else {
////                    // Tunnel is already up and running, check lease and account
////                    if status?.hasGateway() ?? false {
////                        if !Config.shared.accountActive() || !Config.shared.leaseActive() {
////                            //self.log.w("start: Lease expired, showing alert dialog")
////                            //self.showExpiredAlert()
////                            return self.afterStart(done)
////                        } else {
////                            self.expiration.update(Config.shared.account()!)
////                            return self.afterStart(done)
////                        }
////                    } else {
////                         return self.afterStart(done)
////                    }
////                }
//            }}
//        }
//    }

//    private func afterStart(_ done: @escaping Callback<Void>) {
//        self.log.v("Start procedure done")
//        self.working = false
//        done(nil, nil)
//
//        onBackground {
//            if !Config.shared.accountActive() {
//                PaymentService.shared.refreshProductsAfterStart()
//            }
//
//            // Check lease on every fresh app start if using vpn
//            if Config.shared.vpnEnabled() {
//                self.recheckActiveLeaseAfterActivating()
//            }
//        }
//    }

    func foreground() {

//
//                    if let pause = status?.pauseSeconds {
//                        if self.timerSeconds != pause {
//                            self.log.v("Foreground: syncing pause timer, NETX reported \(pause)s")
//                            if self.timerSeconds == 0 {
//                                self.startTimer(seconds: pause)
//                            } else if pause == 0 {
//                                self.stopTimer()
//                            } else {
//                                self.timerSeconds = pause
//                            }
//                        }
//                    }
//
//                    self.expiration.update(Config.shared.account())
//
//                    // Check if tunnel should stay active
//                    if status?.hasGateway() ?? false {
//                        if !Config.shared.accountActive() || !Config.shared.leaseActive() {
//                            // No active lease
//                            //self.showExpiredAlert()
//                            //self.log.w("Foreground: lease expired, showing alert dialog")
//                        } else {
//                            if (Config.shared.hasLease()) {
//                                self.recheckActiveLeaseAfterActivating()
//                                self.log.v("Foreground: synced (lease active)")
//                            } else {
//                                self.log.w("Foreground: synced: missing lease")
//                            }
//                        }
//                    } else { // TODO: if cloud, and no active account, popup cta here?
//                        self.log.v("Foreground: synced")
//                    }
//                }}
//            }
//        }
//
//        // We don't need the background task if we are in foreground
//        if timerSeconds != 0 {
//            self.endBackgroundTask()
//        }
    }
//
    func background() {
//        self.log.v("App entered background")
//        if timerSeconds != 0 {
//            self.registerBackgroundTask()
//        }
    }

//    private func showExpiredAlert() {
//        onMain {
//            guard !self.expiredAlertShown else { return }
//            self.expiredAlertShown = true
//
//            // Hide the current displaying sheet, if any
//            self.onAccountExpired()
//
//            // Wait (seems to be necessary)
//            onBackground {
//                sleep(1)
//                onMain {
//                    // Show the alert
//                    self.showExpired = true
//                }
//            }
//        }
//    }

    func turnOn() {
        
    }

    func turnOff() {
        // TODO: in cloud mode, initiate untimed pause
        self.working = false
        //self.mainSwitch = false
    }

    func switchMain(activate: Bool,
                    noPermissions: @escaping Ok<Void>,
                    showRateScreen: @escaping Ok<Void>,
                    dnsProfileConfigured: @escaping Ok<Void>
    ) {
//        onBackground {
//                self.log.v("User action: switchMain: \(activate)")
//                self.working = true
//
//                let cfg = Config.shared
//                if activate {
//                    // Turning on
//                    if (!cfg.accountActive()) {
//                        // Payment popup
//                        self.log.v("User action: switchMain: no active account")
//                        self.working = false
//                        self.mainSwitch = false
//                        return onMain { noActiveAccount(()) }
//                    }
//
//                    // Ask for permission to send notifications after power on
//                    defer { self.notification.askForPermissions() }
//
//                    if (!cfg.vpnEnabled()) {
//                        // Cloud mode
//                        self.api.getCurrentDevice { error, device in
//                            guard error == nil else {
//                                return self.handleError(CommonError.unknownError, cause: error)
//                            }
//
//                            guard let tag = device?.device_tag else {
//                                return self.handleError(CommonError.unknownError, cause: "No device tag")
//                            }
//
//                            //cfg.setDeviceTag(tag: tag)
//
////                            self.networkDns.saveBlokadaNetworkDns(tag: tag, name: self.envRepo.deviceName) { error, _ in
////                                guard error == nil else {
////                                    return self.handleError(CommonError.unknownError, cause: error)
////                                }
////
////                                // It is possible the profile is already activated by the user
////                                self.networkDns.isBlokadaNetworkDnsEnabled { error, dnsEnabled in
////                                    guard dnsEnabled == true else {
////                                        // If not, show the prompt
////                                        self.mainSwitch = false
////                                        self.working = false
////                                        self.log.v("User action: switchMain: done, dns profile unactivated")
////                                        return onMain { dnsProfileConfigured(()) }
////                                    }
////
////                                    self.mainSwitch = true
////                                    self.working = false
////                                    self.log.v("User action: switchMain: done")
////
////                                }
////                            }
//                        }
//                    } else {
//                        // Plus mode
//                        self.network.queryStatus { error, status in onMain {
//                            guard error == nil else {
//                                if error is CommonError && (error as! CommonError) == CommonError.vpnNoPermissions {
//                                    return onMain {
//                                        self.log.v("Showing ask for VPN sheet")
//                                        self.working = false
//                                        self.mainSwitch = false
//                                        self.syncUiWithConfig()
//                                        return onMain { noPermissions(()) }
//                                    }
//                                } else {
//                                    return self.handleError(CommonError.failedTunnel, cause: error)
//                                }
//                            }
//
//                            guard let status = status else {
//                                return self.handleError(CommonError.failedTunnel, cause: error)
//                            }
//
//                            if cfg.hasLease() && cfg.leaseActive() {
//                                // Vpn should be on, and lease is OK
//                                self.vpn.applyGatewayFromConfig() { error, _ in onMain {
//                                    guard error == nil else {
//                                        cfg.clearLease()
//                                        return self.handleError(CommonError.failedTunnel, cause: error)
//                                    }
//
//                                    self.expiration.update(cfg.account())
//                                    self.recheckActiveLeaseAfterActivating()
//                                    self.log.v("User action: switchMain: done")
//                                }}
//                            } else if cfg.hasLease() && cfg.accountActive() {
//                                // Vpn should be on, but lease expired, refresh
//                                self.refreshLease { error, _ in onMain {
//                                    guard error == nil else {
//                                        cfg.clearLease()
//                                      return self.handleError(CommonError.failedFetchingData, cause: error)
//                                    }
//
//                                    self.vpn.applyGatewayFromConfig() { error, _ in onMain {
//                                        guard error == nil else {
//                                            cfg.clearLease()
//                                            return self.handleError(CommonError.failedTunnel, cause: error)
//                                        }
//
//                                        self.expiration.update(cfg.account())
//                                        self.log.v("User action: switchMain: done")
//                                    }}
//                                }}
//                            } else {
//                                // Vpn should be on, but account has expired
//                                cfg.clearLease()
//                                return self.handleError(CommonError.accountInactive, cause: error)
//                            }
//                        }}
//                    }
//                } else {
//                    // Turning off
//                    self.network.queryStatus { error, status in onMain {
//                        guard error == nil, let status = status else {
//                            if error is CommonError && (error as! CommonError) == CommonError.vpnNoPermissions {
//                                self.working = false
//                                self.mainSwitch = false
//                                self.stopTimer()
//                                return self.log.v("User action: switchMain: done (no vpn perms)")
//                            }
//                            return self.handleError(CommonError.failedTunnel, cause: error)
//                        }
//
//                        if status.active {
//                            // Turn off VPN
//                            self.stopTimer()
//                            self.vpn.turnOffEverything { error, _ in onMain {
//                                guard error == nil else {
//                                    return self.handleError(CommonError.failedTunnel, cause: error)
//                                }
//
//                                self.error = nil
//                                self.log.v("User action: switchMain: done")
//                            }}
//                        } else {
//                            // Turning off Cloud is not possible, show a message TODO?
//                            self.working = false
//                            self.mainSwitch = false
//                            self.stopTimer()
//                            self.log.v("User action: switchMain: done")
//                        }
//                    }}
//                }
//            }
    }

//    private func recheckActiveLeaseAfterActivating() {
//        onBackground {
//            self.log.v("Check lease after start")
//            self.api.getCurrentLease { error, lease in
//                guard error == nil else { return }
//
//                if !(lease?.isActive() ?? false) {
//                    self.log.w("Lease got deactivated, recreating")
//                    self.expiration.update(nil)
//                }
//            }
//        }
//    }
//
    func switchVpn(activate: Bool, noPermissions: @escaping Ok<Void>) {
//        onBackground {
//            self.ensureAppStartedSuccessfully { error, _ in
//                guard error == nil else {
//                    return
//                }
//
//                Config.shared.setVpnEnabled(activate)
//                self.log.v("User action: switchVpn: \(activate)")
//
//                self.network.queryStatus { error, status in onMain {
//                    guard error == nil else {
//                        Config.shared.setVpnEnabled(false)
//                        if error is CommonError && (error as! CommonError) == CommonError.vpnNoPermissions {
//                            return onMain {
//                                self.log.v("Showing ask for VPN sheet")
//                                self.syncUiWithConfig()
//                                return noPermissions(())
//                            }
//                        } else {
//                            return self.handleError(CommonError.failedTunnel, cause: error)
//                        }
//                    }
//
//                    guard let status = status else {
//                        Config.shared.setVpnEnabled(false)
//                        return self.handleError(CommonError.failedTunnel, cause: error)
//                    }
//
//                    if activate {
//                        if !Config.shared.accountActive() {
//                            Config.shared.setVpnEnabled(false)
//                            return self.handleError(CommonError.accountInactive)
//                        } else if !Config.shared.hasLease() || !Config.shared.leaseActive() {
//                            self.log.v("No location selected")
//                            onMain { self.syncUiWithConfig() }
//                            Config.shared.setVpnEnabled(false)
//                            return
//                        } else {
//                            self.vpn.applyGatewayFromConfig() { error, _ in onMain {
//                                guard error == nil else {
//                                    Config.shared.setVpnEnabled(false)
//                                    return self.handleError(CommonError.failedVpn, cause: error)
//                                }
//
//                                self.expiration.update(Config.shared.account())
//                                self.recheckActiveLeaseAfterActivating()
//                                self.log.v("User action: switchVpn: done")
//                            }}
//                        }
//                    } else if !activate && Config.shared.hasGateway() {
//                        self.vpn.turnOffEverything { error, _ in onMain {
//                            guard error == nil else {
//                                return self.handleError(CommonError.failedVpn, cause: error)
//                            }
//
//                            self.log.v("User action: switchVpn: done")
//                        }}
//                    } else {
//                        self.log.v("User action: switchVpn: done")
//                    }
//                }}
//            }
//        }
    }
//
    func switchGateway(new: Gateway, noPermissions: @escaping Ok<Void>) {
//        onBackground {
//            self.ensureAppStartedSuccessfully { error, _ in
//                guard error == nil else {
//                    return
//                }
//
//                self.log.v("User action: switchGateway: \(new.public_key)")
//                self.working = true
//
//                let leaseRequest = LeaseRequest(
//                    account_id: Config.shared.accountId(),
//                    public_key: Config.shared.publicKey(),
//                    gateway_id: new.public_key,
//                    alias: self.envRepo.aliasForLease
//                )
//
//                self.api.postLease(request: leaseRequest) { error, lease in onMain {
//                    guard error == nil else {
//                        if let e = error as? CommonError, e == CommonError.tooManyLeases {
//                            return self.api.deleteLeasesWithAliasOfCurrentDevice(id: Config.shared.accountId()) { deleteError, _ in
//                                if let deleteError = deleteError {
//                                    // Return the initial error instead
//                                    self.log.e("Deleting existing lease for same alias failed".cause(deleteError))
//                                    return self.handleError(CommonError.failedFetchingData, cause: error)
//                                }
//
//                                // We managed to free up the lease limit, try creating the lease again
//                                return self.switchGateway(new: new, noPermissions: noPermissions)
//                            }
//                        } else {
//                            return self.handleError(CommonError.failedFetchingData, cause: error)
//                        }
//                    }
//
//                    Config.shared.setLease(lease!, new)
//
//                    self.vpn.applyGatewayFromConfig() { error, _ in
//                        onMain {
//                            guard error == nil else {
//                                if error is CommonError && (error as! CommonError) == CommonError.vpnNoPermissions {
//                                    return onMain {
//                                        self.log.v("Showing ask for VPN sheet")
//                                        self.working = false
//                                        self.syncUiWithConfig()
//                                        return noPermissions(())
//                                    }
//                                } else {
//                                    return self.handleError(CommonError.failedTunnel, cause: error)
//                                }
//                            }
//
//                            self.expiration.update(Config.shared.account())
//                            self.log.v("User action: switchGateway: done")
//                        }
//                    }
//                }}
//            }
//        }
    }

    func turnVpnOffAfterExpired() {
//       onBackground {
//           self.ensureAppStartedSuccessfully { error, _ in
//               guard error == nil else {
//                   return
//               }
//
//               self.log.v("User action: turnVpnOffAfterExpired")
//
//               Config.shared.setVpnEnabled(false)
//               Config.shared.clearLease()
//               self.vpn.turnOffEverything { _, _ in }
//
//               self.network.queryStatus { error, status in onMain {
//                   guard error == nil else {
//                        self.mainSwitch = false
//                        self.working = false
//                        return self.handleError(CommonError.failedTunnel, cause: error)
//                   }
//
//                   self.log.v("User action: turnVpnOffAfterExpired done")
//               }}
//           }
//       }
   }

    /**
        Private methods used by those public entrypoints
    */

    private func syncUiWithConfig() {
//        if Config.shared.hasLease() && self.selectedGateway?.public_key != Config.shared.gateway()?.public_key {
//            self.selectedGateway = Config.shared.gateway()
//        } else if !Config.shared.hasLease() && self.selectedGateway != nil {
//            self.selectedGateway = nil
//        }

        self.vpnEnabled = Config.shared.vpnEnabled()
    }

    private func syncUiWithTunnel(done: @escaping Callback<NetworkStatus>) {
        self.log.v("Sync UI with NETX")
        
        // Blokada Cloud may be configured, but when there is no active account, it will be passthrough.
        if !self.accountActive {
            return onMain {
                //self.mainSwitch = false
                self.working = false
                return done(nil, NetworkStatus.disconnected())
            }
        }

//        networkDns.isBlokadaNetworkDnsEnabled { error, dnsEnabled in onMain {
//            guard error == nil else {
//                self.log.w("Could not get NetworkDns state".cause(error))
//                self.mainSwitch = false
//                self.working = false
//                return done(error, nil)
//            }
//
//            let mainSwitch = dnsEnabled ?? false
//
//            self.network.queryStatus { error, status in onMain {
//                guard let status = status else {
//                    self.log.v("  NETX not active".cause(error))
//                    self.mainSwitch = mainSwitch
//                    Config.shared.setVpnEnabled(false)
//                    self.working = false
//                    return done(error, nil)
//                }
//
//                if status.inProgress {
//                    self.log.v(" NETX in progress")
//                    //self.mainSwitch = false
//                    //self.vpnEnabled = false
//                    self.working = true
//                    return done(nil, status)
//                } else if status.active {
//                    self.log.v(" NETX active")
//                    if status.hasGateway() {
//                        self.log.v("  Connected to gateway: \(status.gatewayId!)")
//                        self.mainSwitch = true
//                        Config.shared.setVpnEnabled(true)
//                        self.working = false
//
//                        if !Config.shared.hasLease() || Config.shared.gateway()?.public_key != status.gatewayId! {
//                            self.log.w("Gateway and lease mismatch, disconnecting VPN")
//                            self.turnVpnOffAfterExpired()
//                            return done(nil, nil)
//                        } else {
//                            return done(nil, status)
//                        }
//                    } else {
//                        self.log.w(" NETX in Libre mode, this should not happen")
//                        self.mainSwitch = true
//                        Config.shared.setVpnEnabled(false)
//                        self.working = false
//                        return done(nil, status)
//                    }
//                } else if (mainSwitch) {
//                    self.log.v(" NETX inactive, Cloud mode")
//                    self.mainSwitch = true
//                    Config.shared.setVpnEnabled(false)
//                    self.working = false
//                    return done(nil, status)
//                } else {
//                    self.log.v(" NETX inactive, app deactivated")
//                    self.mainSwitch = false
//                    Config.shared.setVpnEnabled(false)
//                    self.working = false
//                    return done(nil, status)
//                }
//            }}
//        }}
    }

    private func handleError(_ error: CommonError, cause: Error? = nil) {
        onMain {
            self.log.e("\(error)".cause(cause))

            self.error = mapErrorForUser(error, cause: cause)
            self.working = false
            self.syncUiWithConfig()
        }
    }

    private func refreshLease(done: @escaping Callback<Lease>) {
//        let leaseRequest = LeaseRequest(
//            account_id: Config.shared.accountId(),
//            public_key: Config.shared.publicKey(),
//            gateway_id: Config.shared.lease()!.gateway_id,
//            alias: self.envRepo.aliasForLease
//        )
//
//        self.api.postLease(request: leaseRequest) { error, lease in onMain {
//            guard error == nil else {
//                return done(error, nil)
//            }
//
//            self.api.getGateways { error, gateways in
//                guard error == nil else {
//                    return done(error, nil)
//                }
//
//                if let gateways = gateways {
//                    let currentGateway = gateways.first(where: { it in
//                        it.public_key == lease!.gateway_id
//                    })
//
//                    Config.shared.setLease(lease!, currentGateway!)
//                    done(nil, lease!)
//                } else {
//                    return done("Empty gateways", nil)
//                }
//            }
//        }}
    }

    func showErrorMessage() -> String {
        if self.error?.count ?? 999 > 128 {
            return L10n.errorUnknown
        } else {
            return self.error!
        }
    }

    func registerBackgroundTask() {
        self.log.v("Registering background task")
        timerBackgroundTask = UIApplication.shared.beginBackgroundTask { [weak self] in
            self?.stopTimer()
        }
        assert(timerBackgroundTask != .invalid)
    }

    private func shouldShowRateScreen() -> Bool {
        return self.blockedCounter >= 40 && !Config.shared.firstRun() && !Config.shared.rateAppShown()
    }
}