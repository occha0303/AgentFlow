package com.agentflow.backend.ai.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

@Service
public class UrlReaderService {

	private static final int MAX_REDIRECTS = 5;
	private static final int MAX_DOWNLOAD_BYTES = 2_000_000;
	private static final int MAX_CONTENT_CHARACTERS = 12_000;
	private static final Pattern CHARSET_PATTERN = Pattern.compile("charset=([^; ]+)", Pattern.CASE_INSENSITIVE);

	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

	public WebPageContent read(String rawUrl) {
		URI currentUrl = toUri(rawUrl);

		for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
			UrlSafetyValidator.validatePublicHttpUrl(currentUrl);
			HttpResponse<InputStream> response = request(currentUrl);
			int status = response.statusCode();

			if (status >= 300 && status < 400) {
				try (InputStream ignored = response.body()) {
					if (redirectCount == MAX_REDIRECTS) {
						throw new IllegalStateException("UrlReaderTool failed: too many redirects");
					}
					String location = response.headers().firstValue("Location")
							.orElseThrow(() -> new IllegalStateException("UrlReaderTool failed: redirect has no location"));
					currentUrl = currentUrl.resolve(location);
				}
				continue;
			}

			if (status < 200 || status >= 300) {
				try (InputStream ignored = response.body()) {
					throw new IllegalStateException("UrlReaderTool failed: page returned HTTP " + status);
				} catch (IOException exception) {
					throw new IllegalStateException("UrlReaderTool failed: could not close page response");
				}
			}

			return extractPage(currentUrl, response);
		}

		throw new IllegalStateException("UrlReaderTool failed: too many redirects");
	}

	private URI toUri(String rawUrl) {
		try {
			return new URI(rawUrl);
		} catch (URISyntaxException | NullPointerException exception) {
			throw new IllegalArgumentException("UrlReaderTool failed: invalid URL");
		}
	}

	private HttpResponse<InputStream> request(URI uri) {
		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(Duration.ofSeconds(15))
				.header("User-Agent", "AgentFlow/1.0")
				.GET()
				.build();
		try {
			return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("UrlReaderTool failed: page request was interrupted");
		} catch (IOException exception) {
			throw new IllegalStateException("UrlReaderTool failed: network request failed");
		}
	}

	private WebPageContent extractPage(URI url, HttpResponse<InputStream> response) {
		String contentType = response.headers().firstValue("Content-Type").orElse("text/html");
		if (!isReadableText(contentType)) {
			close(response.body());
			throw new IllegalStateException("UrlReaderTool failed: page is not HTML or text content");
		}

		String html;
		try (InputStream inputStream = response.body()) {
			html = new String(readLimited(inputStream), charsetFrom(contentType));
		} catch (IOException exception) {
			throw new IllegalStateException("UrlReaderTool failed: could not read page content");
		}

		Document document = Jsoup.parse(html, url.toString());
		document.select("script, style, nav, footer, header, aside, form, noscript, svg").remove();
		String title = document.title().trim();
		String text = document.body() == null ? "" : document.body().text().trim();
		boolean truncated = text.length() > MAX_CONTENT_CHARACTERS;
		if (truncated) {
			text = text.substring(0, MAX_CONTENT_CHARACTERS) + "\n[Content truncated]";
		}
		return new WebPageContent(title, url.toString(), text);
	}

	private byte[] readLimited(InputStream inputStream) throws IOException {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			byte[] buffer = new byte[8192];
			int bytesRead;
			while ((bytesRead = inputStream.read(buffer)) != -1) {
				if (output.size() + bytesRead > MAX_DOWNLOAD_BYTES) {
					throw new IllegalStateException("UrlReaderTool failed: page content is too large");
				}
				output.write(buffer, 0, bytesRead);
			}
			return output.toByteArray();
		}
	}

	private Charset charsetFrom(String contentType) {
		Matcher matcher = CHARSET_PATTERN.matcher(contentType);
		if (!matcher.find()) {
			return StandardCharsets.UTF_8;
		}
		try {
			return Charset.forName(matcher.group(1).replace("\"", ""));
		} catch (RuntimeException exception) {
			return StandardCharsets.UTF_8;
		}
	}

	private boolean isReadableText(String contentType) {
		String normalizedContentType = contentType.toLowerCase(java.util.Locale.ROOT);
		return normalizedContentType.contains("text/html") || normalizedContentType.contains("application/xhtml+xml")
				|| normalizedContentType.contains("text/plain");
	}

	private void close(InputStream inputStream) {
		try {
			inputStream.close();
		} catch (IOException ignored) {
			// The response is already unusable; retain the more useful content-type error.
		}
	}
}
