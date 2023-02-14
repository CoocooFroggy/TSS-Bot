# TSS-Bot
A Discord bot that check signing status of firmwares and check validity of blobs / download build manifests.

## Commands
- `/tss [device]`: Check signing status of a device. Will request a BM or URL to firmware
- `/verifyblob`: Upload a blob, and then reply with a BM or URL to firmware to validate that blob.
- `/bm [URL]`: Download a BuildManifest from a URL—either iPSW or OTA
- `/blobinfo [blob]`: Get all the information from a blob using img4tool

## Environment Variables
- `TSSBOT_TOKEN`: Discord token
