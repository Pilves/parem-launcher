// Grants Parem WRITE_SECURE_SETTINGS over WebUSB. Fixed package allowlist:
// nothing from the URL ever reaches the shell command, so this page cannot
// be pointed at any other app.
import { Adb, AdbDaemonTransport, AdbDaemonWebUsbDeviceManager, AdbWebCredentialStore } from "./vendor/tango.bundle.js";

const ALLOWED = ["com.parem.launcher", "com.parem.launcher.debug"];
const PERMISSION = "android.permission.WRITE_SECURE_SETTINGS";

const button = document.getElementById("grant");
const status = document.getElementById("status");
const say = (text) => { status.textContent = text; };

const requested = new URLSearchParams(location.search).get("pkg");
const pkg = requested === null ? ALLOWED[0] : ALLOWED.find((p) => p === requested);
if (pkg) {
  document.getElementById("command").textContent = `adb shell pm grant ${pkg} ${PERMISSION}`;
}

const manager = AdbDaemonWebUsbDeviceManager.BROWSER;
if (!pkg) {
  button.disabled = true;
  say("This link is not a Parem link. Nothing will be granted.");
} else if (!manager) {
  button.disabled = true;
  say("This browser cannot talk to USB devices. Open this page in Chrome or Edge on a computer, or use the command below.");
}

button.addEventListener("click", async () => {
  button.disabled = true;
  let adb;
  try {
    say("Pick your phone in the browser's list…");
    const device = await manager.requestDevice();
    if (!device) {
      say("No phone picked.");
      return;
    }
    let connection;
    try {
      connection = await device.connect();
    } catch (e) {
      // A native adb server on this computer holds the USB interface
      say("The phone is busy with another adb program. Run \"adb kill-server\" in a terminal (or close Android Studio), then try again.");
      console.error(e);
      return;
    }
    say("Tap Allow on the phone if it asks about USB debugging…");
    const transport = await AdbDaemonTransport.authenticate({
      serial: device.serial,
      connection,
      credentialStore: new AdbWebCredentialStore("Parem grant page"),
    });
    adb = new Adb(transport);
    const shell = adb.subprocess.noneProtocol;

    const path = await shell.spawnWaitText(["pm", "path", pkg]);
    if (!path.includes("package:")) {
      say("Parem is not installed on this phone.");
      return;
    }
    const out = (await shell.spawnWaitText(["pm", "grant", pkg, PERMISSION])).trim();
    const dump = await shell.spawnWaitText(["dumpsys", "package", pkg]);
    if (dump.includes(`${PERMISSION}: granted=true`)) {
      say("Done. Parem can now switch grayscale. You can unplug the phone.");
    } else {
      say(`The phone refused: ${out || "unknown error"}. Some phones need an extra switch in Developer options, such as "USB debugging (Security settings)".`);
    }
  } catch (e) {
    console.error(e);
    say(`Something went wrong: ${e && e.message ? e.message : e}. Unplug the phone, plug it back in, and try again.`);
  } finally {
    if (adb) await adb.close().catch(() => {});
    button.disabled = !pkg || !manager;
  }
});
