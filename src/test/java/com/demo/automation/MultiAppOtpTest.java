package com.demo.automation;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class MultiAppOtpTest {

    private static final String PRIMARY_PKG = "com.example.securebank";
    private static final String SECONDARY_PKG = "com.example.securetoken";
    private static final String TOKEN_DEEP_LINK = "securetoken://auth?requestId=req_98765";
    private AndroidDriver driver;
    private WebDriverWait wait;
    private boolean passed = false;
    private String failureReason = "";

    /** Reads -D system property first, then environment variable. */
    private static String config(String name) {
        String v = System.getProperty(name);
        if (v == null || v.isEmpty()) {
            v = System.getenv(name);
        }
        if (v == null || v.isEmpty()) {
            throw new IllegalStateException("Missing required config: " + name);
        }
        return v;
    }

    private static String byId(String pkg, String id) {
        return pkg + ":id/" + id;
    }

    @BeforeMethod
    public void setUp() throws Exception {
        String username = config("BROWSERSTACK_USERNAME");
        String accessKey = config("BROWSERSTACK_ACCESS_KEY");
        String primaryApp = config("PRIMARY_APP");     // bs://<PRIMARY_APP_ID>
        String secondaryApp = config("SECONDARY_APP"); // bs://<SECONDARY_APP_ID>

        MutableCapabilities caps = new MutableCapabilities();
        caps.setCapability("platformName", "android");
        caps.setCapability("appium:automationName", "UiAutomator2");
        caps.setCapability("appium:deviceName", "Samsung Galaxy S23");
        caps.setCapability("appium:platformVersion", "13.0");
        caps.setCapability("appium:app", primaryApp);

        HashMap<String, Object> bstackOptions = new HashMap<>();
        bstackOptions.put("userName", username);
        bstackOptions.put("accessKey", accessKey);
        bstackOptions.put("projectName", "Multi App OTP Demo");
        bstackOptions.put("buildName", "multi-app-otp-build");
        bstackOptions.put("sessionName", "Bank + Token app OTP flow");
        caps.setCapability("bstack:options", bstackOptions);
        // BrowserStack pre-installs these alongside the primary app
        caps.setCapability("appium:otherApps", new String[]{secondaryApp});

        driver = new AndroidDriver(new URL("https://hub.browserstack.com/wd/hub"), caps);
        wait = new WebDriverWait(driver, Duration.ofSeconds(20));
    }

    private WebElement el(String pkg, String id) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(
                AppiumBy.id(byId(pkg, id))));
    }

    @Test
    public void otpFromSecondaryAppApprovesTransaction() {
        try {
            // Step 1: login in primary app
            el(PRIMARY_PKG, "txtUsername").sendKeys("demo_user");
            driver.findElement(AppiumBy.id(byId(PRIMARY_PKG, "txtPassword"))).sendKeys("demo_pass");
            driver.findElement(AppiumBy.id(byId(PRIMARY_PKG, "btnLogin"))).click();

            // Step 2: tap the bank app's "Open Token App" button (fires the deep link from the app side)
            el(PRIMARY_PKG, "btnOpenTokenApp").click();

            // Step 3: open the secondary app (pre-installed via otherApps) through its deep link
            Map<String, Object> deepLink = new HashMap<>();
            deepLink.put("url", TOKEN_DEEP_LINK);
            deepLink.put("package", SECONDARY_PKG);
            driver.executeScript("mobile: deepLink", deepLink);
            // Confirm the token app is actually in the foreground
            wait.until(d -> SECONDARY_PKG.equals(driver.getCurrentPackage()));

            // Step 4: read the OTP
            String otp = el(SECONDARY_PKG, "lblGeneratedToken").getText().trim();
            Assert.assertFalse(otp.isEmpty(), "OTP should not be empty");

            // Step 5: back to primary app, session state preserved
            driver.activateApp(PRIMARY_PKG);

            // Step 6: enter OTP and submit
            el(PRIMARY_PKG, "txtOtpInput").sendKeys(otp);
            driver.findElement(AppiumBy.id(byId(PRIMARY_PKG, "btnVerifyOtp"))).click();

            // Step 7: assert success
            String status = el(PRIMARY_PKG, "lblStatus").getText();
            Assert.assertTrue(status.contains("Success: Transaction Approved!"),
                    "Unexpected status: " + status);

            passed = true;
        } catch (Throwable t) {
            failureReason = String.valueOf(t.getMessage()).replace("\"", "'").replace("\n", " ");
            throw t;
        }
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        if (driver == null) {
            return;
        }
        try {
            // Step 8: mark BrowserStack session status
            String status = passed ? "passed" : "failed";
            String reason = passed ? "OTP flow approved" : (failureReason.isEmpty() ? "Test failed" : failureReason);
            if (reason.length() > 250) {
                reason = reason.substring(0, 250);
            }
            String payload = "browserstack_executor: {\"action\": \"setSessionStatus\", \"arguments\": "
                    + "{\"status\":\"" + status + "\", \"reason\": \"" + reason + "\"}}";
            driver.executeScript(payload);
        } finally {
            driver.quit();
        }
    }
}
