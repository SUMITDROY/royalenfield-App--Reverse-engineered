import SwiftUI
import CoreBluetooth

final class Pairing: NSObject, ObservableObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    @Published var status = "Tap Find Android to start."
    @Published var devices: [CBPeripheral] = []
    @Published var ready = false
    @Published var connected = false
    private var central: CBCentralManager!
    private var peer: CBPeripheral?
    private var scanningRequested = false
    private var finished = false
    private let ancs = CBUUID(string: "7905F431-B5CE-4E99-A40F-4B1E122D00D0")
    private let service = CBUUID(string: "EAE10100-238E-4A5C-9D1A-623139AEEE10")
    private let pairingRead = CBUUID(string: "EAE10101-238E-4A5C-9D1A-623139AEEE10")

    override init() { super.init(); central = CBCentralManager(delegate: self, queue: .main) }
    func scan() {
        scanningRequested = true
        guard central.state == .poweredOn else { status = "Enable Bluetooth and allow Bluetooth access for Tripper Pair."; return }
        central.stopScan(); devices.removeAll(); finished = false; ready = false
        central.scanForPeripherals(withServices: nil, options: [CBCentralManagerScanOptionAllowDuplicatesKey: false, CBCentralManagerScanOptionSolicitedServiceUUIDsKey: [ancs]])
        status = "Searching. Start Tripper iPhone Bridge on Android and keep both phones unlocked."
    }
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn { if scanningRequested { scan() } }
        else { connected = false; ready = false; status = "Bluetooth unavailable. Check Bluetooth permissions and switch Bluetooth on." }
    }
    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi RSSI: NSNumber) {
        let solicited = advertisementData[CBAdvertisementDataSolicitedServiceUUIDsKey] as? [CBUUID] ?? []
        let advertised = advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID] ?? []
        let data = advertisementData[CBAdvertisementDataServiceDataKey] as? [CBUUID: Data] ?? [:]
        let marker = data.values.contains { String(data: $0, encoding: .utf8) == "TripperBridge" }
        guard solicited.contains(ancs) || advertised.contains(service) || marker else { return }
        if !devices.contains(where: { $0.identifier == peripheral.identifier }) { devices.append(peripheral) }
    }
    func connect(_ peripheral: CBPeripheral) {
        central.stopScan(); scanningRequested = false; ready = false; finished = false
        peer = peripheral; peripheral.delegate = self; status = "Connecting. Accept Pair on both phones and allow notification sharing."
        central.connect(peripheral)
    }
    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        connected = true; peripheral.discoverServices([service]); status = "Checking Android bridge…"
    }
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard error == nil, let found = peripheral.services?.first(where: { $0.uuid == service }) else {
            status = "Tripper bridge service not found. Start the Android bridge, disconnect, then retry."; return
        }
        peripheral.discoverCharacteristics([pairingRead], for: found)
    }
    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard error == nil, let read = service.characteristics?.first(where: { $0.uuid == pairingRead }) else {
            status = "Pairing characteristic unavailable. Update the Android bridge and retry."; return
        }
        status = "Approve pairing on both phones. The encrypted read verifies the Bluetooth bond."
        peripheral.readValue(for: read)
    }
    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard characteristic.uuid == pairingRead else { return }
        if let error = error { status = "Pairing read failed: \(error.localizedDescription). Unlock both phones and retry."; return }
        guard String(data: characteristic.value ?? Data(), encoding: .utf8) == "Tripper iPhone Bridge" else {
            status = "Unexpected accessory response. Disconnect and select the Android bridge."; return
        }
        ready = true; status = "Bluetooth bond verified. Allow system-notification sharing if asked, then tap Finish Pairing."
    }
    func finish() {
        guard let peer = peer else { return }; finished = ready; central.stopScan(); central.cancelPeripheralConnection(peer)
    }
    func cancel() { finished = false; ready = false; if let peer = peer { central.cancelPeripheralConnection(peer) }; central.stopScan(); status = "Disconnected. Tap Find Android to retry." }
    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        connected = false; ready = false; status = "Connection failed: \(error?.localizedDescription ?? "unknown Bluetooth error"). Retry with both phones unlocked."
    }
    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        connected = false
        status = finished ? "Pairing finished. You can close Tripper Pair. iOS and Android handle the bridge connection. Check Calls / Media ready on Android." : "Disconnected. If pairing did not finish, unlock both phones and retry."
    }
}

@main struct TripperPairApp: App {
    @StateObject private var pairing = Pairing()
    var body: some Scene {
        WindowGroup {
            NavigationStack {
                VStack(alignment: .leading, spacing: 20) {
                    Text("Connect your iPhone to the Android bridge without LightBlue.").font(.headline)
                    Text(pairing.status).accessibilityIdentifier("pairingStatus")
                    Button("Find Android") { pairing.scan() }.buttonStyle(.borderedProminent)
                    List(pairing.devices, id: \.identifier) { device in Button(device.name ?? "Tripper Android bridge") { pairing.connect(device) } }.frame(minHeight: 120, maxHeight: 240)
                    Button("Finish Pairing") { pairing.finish() }.disabled(!pairing.ready).buttonStyle(.borderedProminent)
                    Button("Disconnect / Retry") { pairing.cancel() }
                    Text("Audio stays on iPhone or your headset. After pairing, allow Share System Notifications in the iPhone Bluetooth device settings. This app is a pairing helper; calls and media are handled by the Android bridge.").font(.footnote).foregroundStyle(.secondary)
                    Spacer()
                }.padding().navigationTitle("Tripper Pair")
            }
        }
    }
}
