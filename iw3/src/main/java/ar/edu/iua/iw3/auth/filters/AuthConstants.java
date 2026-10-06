package ar.edu.iua.iw3.auth.filters;

public final class AuthConstants {
	public static final long EXPIRATION_TIME = (60 * 60 * 1000);
	public static final String SECRET = "***JWT_SECRET_REDACTED***";
	
	public static final String AUTH_HEADER_NAME = "Authorization";
	public static final String AUTH_PARAM_NAME = "authtoken";
	public static final String TOKEN_PREFIX = "Bearer ";
}

