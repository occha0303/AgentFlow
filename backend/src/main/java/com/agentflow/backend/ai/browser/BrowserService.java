package com.agentflow.backend.ai.browser;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import com.agentflow.backend.ai.web.UrlSafetyValidator;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import com.microsoft.playwright.options.WaitUntilState;

@Service
public class BrowserService {

	private static final String ELEMENT_SELECTOR = "a[href], button, input, textarea, select, "
			+ "[role='button'], [role='link'], [role='textbox']";
	private static final int MAX_ELEMENTS = 40;
	private static final int MAX_CONTENT = 3_000;
	private static final double TIMEOUT_MS = 15_000;
	private static final String SENSITIVE = "password|passcode|otp|captcha|credit.?card|card.?number|cvv|cvc|"
			+ "api.?key|secret|token|payment|checkout|delete|login|sign.?in|bank|"
			+ "密码|登录|支付|购买|结账|删除|验证码|信用卡|银行卡|密钥|令牌";

	public BrowserPageSnapshot open(String rawUrl) {
		return withPage(rawUrl, session -> snapshot(session.page()));
	}

	/** Reopen and verify a ref from this run's earlier read; this never performs an action. */
	public BrowserActionPayload prepareAction(String rawUrl, String elementRef,
			BrowserActionType action, String value, BrowserPageSnapshot readSnapshot) {
		if (readSnapshot == null || elementRef == null || action == null) {
			throw new IllegalArgumentException("Browser action requires a prior page snapshot and valid ref");
		}
		BrowserElement readElement = readSnapshot.elements().stream()
				.filter(element -> element.ref().equals(elementRef)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Browser element ref was not in the read snapshot"));
		validateValue(action, value);
		return withPage(rawUrl, session -> {
			BrowserPageSnapshot fresh = snapshot(session.page());
			if (!fresh.url().equals(readSnapshot.url())) {
				throw new IllegalStateException("Browser page changed before approval preparation");
			}
			BrowserElement current = fresh.elements().stream()
				.filter(element -> element.ref().equals(elementRef)).findFirst()
				.orElseThrow(() -> new IllegalStateException("Browser element disappeared"));
			if (!sameTarget(readElement, current)) {
				throw new IllegalStateException("Browser element changed before approval preparation");
			}
			validateTarget(session.page(), current, action);
			uniqueMatchingLocator(session.page(), current);
			return new BrowserActionPayload(fresh.url(), elementRef, action,
					action == BrowserActionType.TYPE ? value : null, current);
		});
	}

	/** Called only by BrowserWriteService after a stored human approval. */
	BrowserActionResult executeApproved(BrowserActionPayload payload) {
		if (payload == null || payload.target() == null || payload.browserAction() == null) {
			throw new IllegalArgumentException("Approved browser action payload is invalid");
		}
		validateValue(payload.browserAction(), payload.value());
		return withPage(payload.url(), session -> {
			if (!session.page().url().equals(payload.url())) {
				throw new IllegalStateException("Approved browser page changed");
			}
			Locator target = uniqueMatchingLocator(session.page(), payload.target());
			validateTarget(session.page(), payload.target(), payload.browserAction());
			session.actionWindow().set(payload.browserAction() != BrowserActionType.TYPE);
			try {
				switch (payload.browserAction()) {
					case CLICK, SUBMIT -> target.click();
					case TYPE -> target.fill(payload.value());
				}
				if (payload.browserAction() != BrowserActionType.TYPE) {
					// A short, action-scoped window for same-origin POSTs triggered by the click.
					session.page().waitForTimeout(500);
				}
			} finally {
				session.actionWindow().set(false);
			}
			if (session.blockedUnsafeRequest().get()) {
				throw blocked();
			}
			validateBrowserUrl(toUri(session.page().url()));
			return new BrowserActionResult(session.page().url(), session.page().title(),
					shorten(session.page().locator("body").innerText().trim(), 400));
		});
	}

	private <T> T withPage(String rawUrl, Function<BrowserSession, T> operation) {
		URI requested = toUri(rawUrl);
		validateBrowserUrl(requested);
		AtomicBoolean blocked = new AtomicBoolean(false);
		AtomicBoolean actionWindow = new AtomicBoolean(false);
		try (Playwright playwright = Playwright.create();
				Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
				BrowserContext context = browser.newContext(new Browser.NewContextOptions()
						.setAcceptDownloads(false).setServiceWorkers(ServiceWorkerPolicy.BLOCK))) {
			context.route("**/*", route -> {
				try {
					URI requestUrl = toUri(route.request().url());
					validateBrowserUrl(requestUrl);
					String method = route.request().method();
					if ("GET".equalsIgnoreCase(method) || ("POST".equalsIgnoreCase(method)
							&& actionWindow.get() && sameOrigin(requested, requestUrl))) {
						route.resume();
					} else {
						blocked.set(true);
						route.abort();
					}
				} catch (IllegalArgumentException exception) {
					blocked.set(true);
					route.abort();
				}
			});
			context.routeWebSocket("**/*", route -> route.close());
			Page page = context.newPage();
			page.onPopup(popup -> {
				blocked.set(true);
				popup.close();
			});
			page.onDownload(download -> blocked.set(true));
			try {
				page.navigate(requested.toString(), new Page.NavigateOptions()
						.setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(TIMEOUT_MS));
			} catch (PlaywrightException exception) {
				if (blocked.get()) throw blocked();
				throw new IllegalStateException("BrowserTool failed: page navigation failed", exception);
			}
			validateBrowserUrl(toUri(page.url()));
			if (blocked.get()) throw blocked();
			T result = operation.apply(new BrowserSession(page, actionWindow, blocked));
			if (blocked.get()) throw blocked();
			return result;
		} catch (PlaywrightException exception) {
			throw new IllegalStateException("BrowserTool failed: browser is unavailable", exception);
		}
	}

	private BrowserPageSnapshot snapshot(Page page) {
		List<BrowserElement> elements = new ArrayList<>();
		Locator candidates = page.locator(ELEMENT_SELECTOR);
		int candidateCount = Math.min(candidates.count(), 300);
		for (int i = 0; i < candidateCount && elements.size() < MAX_ELEMENTS; i++) {
			Locator candidate = candidates.nth(i);
			if (candidate.isVisible()) {
				elements.add(describe(candidate, "e" + (elements.size() + 1)));
			}
		}
		return new BrowserPageSnapshot(page.url(), shorten(page.title().trim(), 200),
				shorten(page.locator("body").innerText().trim(), MAX_CONTENT), List.copyOf(elements));
	}

	private BrowserElement describe(Locator locator, String ref) {
		String tag = String.valueOf(locator.evaluate("element => element.tagName.toLowerCase()"));
		String role = attr(locator, "role");
		if (role.isBlank()) {
			role = switch (tag) {
				case "a" -> "link";
				case "button" -> "button";
				case "textarea" -> "textbox";
				case "input" -> switch (attr(locator, "type").toLowerCase(Locale.ROOT)) {
					case "button", "submit", "reset" -> "button";
					case "checkbox" -> "checkbox";
					default -> "textbox";
				};
				case "select" -> "combobox";
				default -> "";
			};
		}
		return new BrowserElement(ref, tag, role, shorten(locator.innerText().trim(), 120),
				shorten(attr(locator, "aria-label"), 120), attr(locator, "type").toLowerCase(Locale.ROOT),
				shorten(attr(locator, "href"), 300));
	}

	private Locator uniqueMatchingLocator(Page page, BrowserElement descriptor) {
		Locator semantic = semanticLocator(page, descriptor);
		if (semantic != null && semantic.count() == 1 && semantic.isVisible()
				&& sameTarget(descriptor, describe(semantic, descriptor.ref()))) {
			return semantic;
		}
		Locator candidates = page.locator(ELEMENT_SELECTOR);
		Locator match = null;
		int count = Math.min(candidates.count(), 300);
		for (int i = 0; i < count; i++) {
			Locator candidate = candidates.nth(i);
			if (candidate.isVisible() && sameTarget(descriptor, describe(candidate, descriptor.ref()))) {
				if (match != null) throw new IllegalStateException("Browser target is ambiguous");
				match = candidate;
			}
		}
		if (match == null) throw new IllegalStateException("Approved browser target changed or disappeared");
		return match;
	}

	private Locator semanticLocator(Page page, BrowserElement descriptor) {
		String name = descriptor.ariaLabel().isBlank() ? descriptor.text() : descriptor.ariaLabel();
		if (descriptor.role().equals("textbox") && !descriptor.ariaLabel().isBlank()) {
			return page.getByLabel(descriptor.ariaLabel(), new Page.GetByLabelOptions().setExact(true));
		}
		if (name.isBlank()) return null;
		AriaRole role = switch (descriptor.role()) {
			case "button" -> AriaRole.BUTTON;
			case "link" -> AriaRole.LINK;
			case "textbox" -> AriaRole.TEXTBOX;
			default -> null;
		};
		return role == null ? null : page.getByRole(role, new Page.GetByRoleOptions()
				.setName(name).setExact(true));
	}

	private boolean sameTarget(BrowserElement a, BrowserElement b) {
		return a.tag().equals(b.tag()) && a.role().equals(b.role()) && a.text().equals(b.text())
				&& a.ariaLabel().equals(b.ariaLabel()) && a.inputType().equals(b.inputType())
				&& a.href().equals(b.href());
	}

	private void validateTarget(Page page, BrowserElement element, BrowserActionType action) {
		Locator target = uniqueMatchingLocator(page, element);
		String context = (page.url() + " " + page.title() + " " + element.text() + " "
				+ element.ariaLabel() + " " + element.href() + " " + attr(target, "name")
				+ " " + attr(target, "placeholder") + " " + attr(target, "autocomplete")).toLowerCase(Locale.ROOT);
		if (context.matches("(?s).*?(" + SENSITIVE + ").*")) {
			throw new IllegalArgumentException("Browser action blocked: sensitive page or target");
		}
		if ("_blank".equalsIgnoreCase(attr(target, "target"))) {
			throw new IllegalArgumentException("Browser action blocked: multiple tabs are not supported");
		}
		Locator form = target.locator("xpath=ancestor::form");
		if (form.count() == 1) {
			String formText = form.innerText().toLowerCase(Locale.ROOT);
			String formAction = attr(form, "action");
			if (form.locator("input[type='password'], input[autocomplete^='cc-']").count() > 0
					|| formText.matches("(?s).*?(" + SENSITIVE + ").*")) {
				throw new IllegalArgumentException("Browser action blocked: sensitive form");
			}
			if (!formAction.isBlank()) {
				URI actionUrl = toUri(page.url()).resolve(formAction);
				validateBrowserUrl(actionUrl);
				if (!sameOrigin(toUri(page.url()), actionUrl)) {
					throw new IllegalArgumentException("Browser action blocked: cross-origin form");
				}
			}
		}
		if (action == BrowserActionType.TYPE) {
			if (!(element.tag().equals("textarea") || (element.tag().equals("input")
					&& (element.inputType().isBlank() || element.inputType().equals("text")
							|| element.inputType().equals("search"))))) {
				throw new IllegalArgumentException("Browser TYPE supports ordinary text fields only");
			}
		} else if (action == BrowserActionType.SUBMIT) {
			if (!(element.tag().equals("button") || element.tag().equals("input"))
					|| !(element.inputType().isBlank() || element.inputType().equals("submit"))) {
				throw new IllegalArgumentException("Browser SUBMIT requires a submit button");
			}
			if (!context.contains("demo") && !context.contains("example")) {
				throw new IllegalArgumentException("Browser SUBMIT is limited to public demo forms");
			}
			if (form.count() != 1) {
				throw new IllegalArgumentException("Browser SUBMIT blocked: not a safe demo form");
			}
		} else if (!(element.role().equals("button") || element.role().equals("link"))) {
			throw new IllegalArgumentException("Browser CLICK requires a button or link");
		}
	}

	private void validateValue(BrowserActionType action, String value) {
		if (action == BrowserActionType.TYPE) {
			if (value == null || value.isBlank() || value.length() > 2_000
					|| value.toLowerCase(Locale.ROOT).matches("(?s).*?(sk-[a-z0-9]{10,}|api[_-]?key|password|credit.?card).*")) {
				throw new IllegalArgumentException("Browser TYPE requires non-secret ordinary text (1-2000 characters)");
			}
		} else if (value != null && !value.isBlank()) {
			throw new IllegalArgumentException("Browser action value is only allowed for TYPE");
		}
	}

	private String attr(Locator locator, String name) {
		String value = locator.getAttribute(name);
		return value == null ? "" : value.trim();
	}

	private boolean sameOrigin(URI a, URI b) {
		return a.getScheme().equalsIgnoreCase(b.getScheme())
				&& a.getHost().equalsIgnoreCase(b.getHost()) && effectivePort(a) == effectivePort(b);
	}

	private int effectivePort(URI url) {
		return url.getPort() >= 0 ? url.getPort() : ("https".equalsIgnoreCase(url.getScheme()) ? 443 : 80);
	}

	private URI toUri(String rawUrl) {
		try { return new URI(rawUrl); }
		catch (URISyntaxException | NullPointerException exception) {
			throw new IllegalArgumentException("BrowserTool failed: invalid URL");
		}
	}

	private void validateBrowserUrl(URI uri) {
		if (uri.getRawUserInfo() != null) throw blocked();
		try { UrlSafetyValidator.validatePublicHttpUrl(uri); }
		catch (IllegalArgumentException exception) {
			if (exception.getMessage() != null && exception.getMessage().contains("blocked unsafe URL")) throw blocked();
			throw new IllegalArgumentException("BrowserTool failed: host could not be resolved");
		}
	}

	private String shorten(String text, int limit) {
		return text.length() <= limit ? text : text.substring(0, limit - 3) + "...";
	}

	private IllegalArgumentException blocked() {
		return new IllegalArgumentException("BrowserTool blocked unsafe URL");
	}

	private record BrowserSession(Page page, AtomicBoolean actionWindow, AtomicBoolean blockedUnsafeRequest) { }
}
