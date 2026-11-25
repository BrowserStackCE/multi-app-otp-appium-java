# Multi-App OTP Testing with Appium Java on BrowserStack App Automate

A sample showing how to test a flow that spans **two Android apps in one session** on
[BrowserStack App Automate](https://www.browserstack.com/app-automate) real devices, using
Appium (Java), TestNG and no BrowserStack SDK. A typical use case is a banking app that needs
an OTP from a separate soft-token / authenticator app.

The test shows how to:
>>>>>>> placeholder

- The **primary app** (SecureBank Demo, `com.example.securebank`) is installed with `appium:app`.
- The **secondary app** (SecureToken Demo, `com.example.securetoken`) is pre-installed alongside it
  with `appium:otherApps`.
- Mid-test, the script opens the secondary app through its deep link
  (`securetoken://auth?requestId=req_98765`), reads the OTP, and switches back to the primary app
  with `driver.activateApp()` without resetting the session.

## Contents

```
MultiAppTestProject/
  apps/
    SecureBank-debug.apk     primary app   (com.example.securebank)
    SecureToken-debug.apk    secondary app (com.example.securetoken)
  src/test/java/com/demo/automation/MultiAppOtpTest.java
  pom.xml
```

The two APKs are ready to use. You do not need to build anything.

## Prerequisites

- JDK 11 or later and Maven 3.8+
- A BrowserStack account with App Automate

## 1. Set your BrowserStack credentials

```bash
export BROWSERSTACK_USERNAME=<your-username>
export BROWSERSTACK_ACCESS_KEY=<your-access-key>
```

## 2. Upload the apps to BrowserStack

Run these from the `MultiAppTestProject` folder. Each response contains an `app_url` of the form
`bs://<hash>`; note both values.

```bash
# Primary app
curl -u "$BROWSERSTACK_USERNAME:$BROWSERSTACK_ACCESS_KEY" \
  -X POST "https://api-cloud.browserstack.com/app-automate/upload" \
  -F "file=@apps/SecureBank-debug.apk" \
  -F "custom_id=SecureBankDemo"

# Secondary app
curl -u "$BROWSERSTACK_USERNAME:$BROWSERSTACK_ACCESS_KEY" \
  -X POST "https://api-cloud.browserstack.com/app-automate/upload" \
  -F "file=@apps/SecureToken-debug.apk" \
  -F "custom_id=SecureTokenDemo"
```

## 3. Run the test

```bash
mvn clean test \
  -DPRIMARY_APP=bs://<PRIMARY_APP_ID> \
  -DSECONDARY_APP=bs://<SECONDARY_APP_ID>
```

Results appear in the BrowserStack App Automate dashboard under the project
**Multi App OTP Demo**. The test sets the session status (`passed` / `failed`) itself.

## What the test does

1. Logs in to the SecureBank app (`txtUsername`, `txtPassword`, `btnLogin`).
2. Taps **Open Token App** (`btnOpenTokenApp`) on the verification screen.
3. Opens the SecureToken app through its deep link and waits until it is in the foreground.
4. Reads the OTP from `lblGeneratedToken`.
5. Switches back to SecureBank with `driver.activateApp("com.example.securebank")`.
6. Enters the OTP in `txtOtpInput` and taps `btnVerifyOtp`.
7. Asserts `lblStatus` contains `Success: Transaction Approved!`.
8. Marks the BrowserStack session as passed or failed.

## Configuration notes

- Device and OS are set in `MultiAppOtpTest.setUp()` (currently Samsung Galaxy S23, Android 13.0).
  BrowserStack rejects a device and OS combination it does not offer; see the
  [capability builder](https://www.browserstack.com/app-automate/capabilities) for valid pairs.
- `otherApps` is a top-level capability (`appium:otherApps`), not part of `bstack:options`.
- Credentials are read from environment variables and are never stored in the project.
- The demo OTP is the fixed value `849201`, which the SecureBank app accepts.
