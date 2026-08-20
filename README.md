<img width="1995" height="488" alt="minecraft_title" src="https://github.com/user-attachments/assets/436c38b7-4dc7-4a40-a723-9323f3679512" />

Take your Minecraft worlds with you.

WorldVault is a client-side Fabric mod that syncs singleplayer worlds with **Google Drive** or **Dropbox**.

Play on one computer, sync your world, and continue on another. Your worlds stay in your own cloud storage, no
WorldVault account or separate storage service required.

## Features

* Google Drive and Dropbox support
* Automatic syncing
* Continue worlds across multiple computers
* Download cloud worlds from inside Minecraft
* Sync status shown in the world list
* Only changed files are uploaded
* Conflict protection when local and cloud versions both change

## Getting started

1. Install **Fabric Loader** and **Fabric API**.
2. Put WorldVault in your `mods` folder.
3. Start Minecraft.
4. Open WorldVault settings and connect Google Drive or Dropbox.

That's it! WorldVault will handle syncing your worlds from there.

## Conflicts

If the same world is changed on multiple computers before syncing, WorldVault won't overwrite either version
automatically.

You can choose to:

* **Keep Local**
* **Keep Cloud**
* **Keep Both**

## Privacy

WorldVault does not store your worlds on its own servers. World files are uploaded directly to your Google Drive or
Dropbox account.

Authentication uses OAuth, so your Google or Dropbox password is never given to the mod.

WorldVault also sends a small anonymous usage count once per launch. This can be disabled in the settings.

## Cloud storage

### Google Drive

WorldVault only requests access to files it creates.

### Dropbox

WorldVault stores its files inside:

```text
Apps/WorldVault
```

Only one cloud provider can be connected at a time.

## Supported versions

**Minecraft 1.21.11 & 26.x**

Fabric API is required.

## Building

```sh
./gradlew build
```

Run tests with:

```sh
./gradlew test
```

## Bug reports

If you find a bug, please open an issue and include your Minecraft version, WorldVault version, cloud provider, and any
relevant logs.

## License

WorldVault is available under the **MIT License**.
