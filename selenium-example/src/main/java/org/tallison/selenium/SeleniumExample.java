package org.tallison.selenium;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * based on: https://ksah.in/introduction-to-chrome-headless/
 */

public class SeleniumExample {

    public static void main(String[] args) throws Exception {
        //String chromeDriverPath = "/usr/bin/chromedriver";
        //System.setProperty("webdriver.chrome.driver", chromeDriverPath);
        Path p = Paths.get(args[0]);
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--disable-gpu", "--window-size=1920,1200", "--ignore-certificate-errors", "--silent");
        WebDriver driver = new ChromeDriver(options);

        // Get the login page
        //TODO: add url
        driver.get("insertUrlHere");
        List<WebElement> elements =
                driver.findElements(By.tagName("a"));
        Set<String> links = new HashSet();
        for (WebElement e : elements) {
            try {
                String url = e.getAttribute("href");
                if (! url.startsWith("java")) {
                    links.add(url);
                }
            } catch (Exception ex) {
                //swallow
            }
        }

        System.out.println(driver.getPageSource());
    }

}
