# WorldVault

**Take your Minecraft worlds with you.**

WorldVault adds cloud saves to Minecraft Java, letting you sync your singleplayer worlds with **Google Drive** or *
*Dropbox**.

Play a world on your desktop, let WorldVault sync it, then pick it back up on another computer. Your worlds stay in your
own cloud storage and can be restored if something ever happens to your local save.

No separate WorldVault account. No dedicated WorldVault storage service. Just your Minecraft worlds and the cloud
storage you already use.

## ☁️ Cloud saves for Minecraft

Once you've connected Google Drive or Dropbox, WorldVault takes care of syncing your worlds for you.

Your Minecraft world list shows the current sync status of each world, so you can see at a glance whether a world is
safely stored in the cloud or still needs to be uploaded.

Moving to another computer? Open **Cloud Worlds** and download your world straight back into Minecraft.

## ✨ Features

* **Google Drive & Dropbox support**
  Choose which cloud provider you want to use.

* **Automatic world syncing**
  WorldVault can sync your world when you finish playing and periodically while you play.

* **Play across multiple computers**
  Upload a world from one computer and continue playing it on another.

* **Cloud Worlds browser**
  View and download your cloud saves without leaving Minecraft.

* **Sync status in the world list**
  Quickly see whether a world is synced, waiting to sync, currently uploading, or needs your attention.

* **Smart uploads**
  WorldVault only uploads files that have actually changed instead of uploading your entire world again every time.

* **Conflict protection**
  If both your local and cloud copies have changed, WorldVault won't silently overwrite either one. You decide which
  version to keep.

* **Your storage, your worlds**
  World data is uploaded directly to your Google Drive or Dropbox account.

## 🚀 Getting started

WorldVault is a **client-side Fabric mod**, so you don't need to install anything on a server.

1. Install **Fabric Loader** and **Fabric API**.
2. Put WorldVault in your Minecraft `mods` folder.
3. Start Minecraft.
4. Open the WorldVault settings and connect **Google Drive** or **Dropbox**.
5. Play!

After connecting your account, WorldVault will handle syncing your worlds in the background.

## 🌍 Using a world on another computer

Install WorldVault on the second computer and connect the same cloud account.

From the Minecraft world selection screen, open **Cloud Worlds**. You'll see the worlds stored by WorldVault and can
download the one you want to play.

Once downloaded, it behaves like any other singleplayer world.

When you're done playing, your changes can be synced back to the cloud again.

## 🔄 What happens if the same world changes on two computers?

WorldVault is deliberately careful about this.

If you play the same world on two computers before their changes have been synced, WorldVault may detect that both the
local and cloud versions have changed.

Instead of guessing which one is correct and potentially destroying your progress, WorldVault marks the world as having
a **conflict**.

You can then choose to:

* **Keep Local** - use the world currently on this computer.
* **Keep Cloud** - replace it with the version stored in the cloud.
* **Keep Both** - keep your local world and download the cloud version as a separate world.

WorldVault will never automatically choose one conflicting version over another.

## 🔒 Privacy & security

WorldVault does **not** require an extra account or any personal information. It only needs access to your cloud storage
to upload and download your worlds.

Your world files are sent from your computer to your own Google Drive or Dropbox storage. WorldVault doesn't operate a
server that stores copies of your worlds.

Login credentials are stored using your operating system's secure credential storage whenever available:

| Platform | Storage                                          |
|----------|--------------------------------------------------|
| Windows  | Windows DPAPI                                    |
| macOS    | Keychain                                         |
| Linux    | Secret Service, such as GNOME Keyring or KWallet |

WorldVault uses OAuth to connect to your cloud provider, so you never enter your Google or Dropbox password into the
mod.

## Cloud Storage Providers

### Google Drive

WorldVault only requests access to files it creates through Google Drive. It does **not** receive general access to
everything in your Drive.

### Dropbox

WorldVault uses its own app folder inside Dropbox:

`Apps/WorldVault`

This keeps WorldVault's files separate from the rest of your Dropbox.

### Switching cloud providers

Currently it is only supported to be logged in to a single cloud provider at a time. Switching cloud providers will
remove local sync data. The data on your cloud drive will not be deleted.

## 📦 Supported versions

### 1.21.11 & 26.x

**Fabric API is required.**

## 💾 How are worlds stored?

WorldVault doesn't turn your world into one giant ZIP file every time it syncs.

Instead, your world is mirrored file-by-file in your cloud storage. When you play again, WorldVault can determine which
files actually changed and only upload those files.

For larger worlds, this can significantly reduce how much data needs to be uploaded after each play session.

Downloads are also checked before they're placed into your Minecraft saves folder to make sure the downloaded files
match what was uploaded.

## ⚠️ A note about cloud saves

WorldVault is designed to protect against interrupted uploads and accidental overwrites, but important Minecraft worlds
should never rely on a single backup method.

If you have a world you really care about, keeping an occasional separate backup is still a good idea.

## 🐛 Found a bug?

please report it on the project's issue tracker.

When reporting issues, include your:

* Minecraft version
* WorldVault version
* Cloud provider (Google Drive or Dropbox)
* Relevant logs, if available

## 🛠️ Contributing

To build the project locally:

```sh
./gradlew build
```

To run the test suite:

```sh
./gradlew test
```

The project is split into a shared sync/authentication core and Minecraft-specific modules for the supported game
versions.

If you're contributing or building WorldVault yourself, see the source tree and project configuration for the provider
setup and development tooling.

## 📄 License

WorldVault is available under the **MIT License**. See `LICENSE` for details.
