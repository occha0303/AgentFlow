package com.agentflow.backend.ai.browser;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;

import com.agentflow.backend.ai.web.UrlSafetyValidator;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import com.microsoft.playwright.options.WaitUntilState;

@Service
public class BrowserService {

	private static final int MAX_CONTENT_CHARACTERS = 12_000;
	private static final double NAVIGATION_TIMEOUT_MILLIS = 15_000;

	/**
	 * Opens one public web page in a fresh, non-persistent Chromium context and
	 * returns only its address, title, and readable body text.
	 */
	public BrowserPage open(String rawUrl) {
		URI requestedUrl = toUri(rawUrl);
		validateBrowserUrl(requestedUrl);
		AtomicBoolean blockedUnsafeRequest = new AtomicBoolean(false);

		try (Playwright playwright = Playwright.create();
				Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
				BrowserContext context = browser.newContext(new Browser.NewContextOptions()
						.setAcceptDownloads(false)
						.setServiceWorkers(ServiceWorkerPolicy.BLOCK))) {
			context.route("**/*", route -> {
				if (!"GET".equalsIgnoreCase(route.request().method())) {
					route.abort();
					return;
				}

				try {
					validateBrowserUrl(toUri(route.request().url()));
					route.resume();
				} catch (IllegalArgumentException exception) {
					blockedUnsafeRequest.set(true);
					route.abort();
				}
			});
			// Never connect a page-controlled WebSocket to a remote server.
			context.routeWebSocket("**/*", webSocketRoute -> webSocketRoute.close());

			Page page = context.newPage();
			try {
				page.navigate(requestedUrl.toString(), new Page.NavigateOptions()
						.setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
						.setTimeout(NAVIGATION_TIMEOUT_MILLIS));
			} catch (PlaywrightException exception) {
				if (blockedUnsafeRequest.get()) {
					throw blocked();
				}
				throw new IllegalStateException("BrowserTool failed: page navigation failed");
			}

			URI finalUrl = toUri(page.url());
			try {
				validateBrowserUrl(finalUrl);
			} catch (IllegalArgumentException exception) {
				throw blocked();
			}
			if (blockedUnsafeRequest.get()) {
				throw blocked();
			}

			String title = page.title().trim();
			String text = page.locator("body").innerText().trim();
			return new BrowserPage(finalUrl.toString(), title, limitContent(text));
		} catch (PlaywrightException exception) {
			throw new IllegalStateException("BrowserTool failed: browser is unavailable");
		}
	}

	public String getTitle(BrowserPage page) {
		return page.title();
	}

	public String getText(BrowserPage page) {
		return page.content();
	}

	private URI toUri(String rawUrl) {
		try {
			return new URI(rawUrl);
		} catch (URISyntaxException | NullPointerException exception) {
			throw new IllegalArgumentException("BrowserTool failed: invalid URL");
		}
	}

	private void validateBrowserUrl(URI uri) {
		if (uri.getRawUserInfo() != null) {
			throw blocked();
		}
		try {
			UrlSafetyValidator.validatePublicHttpUrl(uri);
		} catch (IllegalArgumentException exception) {
			if (exception.getMessage() != null && exception.getMessage().contains("blocked unsafe URL")) {
				throw blocked();
			}
			throw new IllegalArgumentException("BrowserTool failed: host could not be resolved");
		}
	}

	private String limitContent(String text) {
		if (text.length() <= MAX_CONTENT_CHARACTERS) {
			return text;
		}
		return text.substring(0, MAX_CONTENT_CHARACTERS) + "\n[Content truncated]";
	}

	private IllegalArgumentException blocked() {
		return new IllegalArgumentException("BrowserTool blocked unsafe URL");
	}
}
