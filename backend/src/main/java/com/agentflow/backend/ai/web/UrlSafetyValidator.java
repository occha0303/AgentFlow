package com.agentflow.backend.ai.web;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URI;
import java.util.Locale;

public final class UrlSafetyValidator {

	private UrlSafetyValidator() {
	}

	public static void validatePublicHttpUrl(URI uri) {
		String scheme = uri.getScheme();
		if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
			throw blocked();
		}

		String host = uri.getHost();
		if (host == null || host.isBlank() || host.equalsIgnoreCase("localhost")
				|| host.toLowerCase(Locale.ROOT).endsWith(".localhost")) {
			throw blocked();
		}

		try {
			for (InetAddress address : InetAddress.getAllByName(host)) {
				if (isPrivateOrSpecialAddress(address)) {
					throw blocked();
				}
			}
		} catch (UnknownHostException exception) {
			throw new IllegalArgumentException("UrlReaderTool failed: host could not be resolved");
		}
	}

	private static boolean isPrivateOrSpecialAddress(InetAddress address) {
		if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
				|| address.isSiteLocalAddress() || address.isMulticastAddress()) {
			return true;
		}

		byte[] bytes = address.getAddress();
		if (address instanceof Inet4Address) {
			int first = Byte.toUnsignedInt(bytes[0]);
			int second = Byte.toUnsignedInt(bytes[1]);
			return first == 0 || first == 10 || first == 127 || (first == 100 && second >= 64 && second <= 127)
					|| (first == 169 && second == 254) || (first == 172 && second >= 16 && second <= 31)
					|| (first == 192 && second == 168) || (first == 198 && (second == 18 || second == 19));
		}
		if (address instanceof Inet6Address) {
			return (bytes[0] & 0xfe) == 0xfc;
		}
		return true;
	}

	private static IllegalArgumentException blocked() {
		return new IllegalArgumentException("UrlReaderTool blocked unsafe URL");
	}
}
