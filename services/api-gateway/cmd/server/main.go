package main

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"net/http/httputil"
	"net/url"
	"os"
	"time"
)

const defaultPort = "8088"

func main() {
	port := envOrDefault("PORT", defaultPort)
	queryAPI := envOrDefault("QUERY_API_URL", "http://localhost:8090")
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	logger.Info("api-gateway starting",
		"port", port,
		"query_api_url", queryAPI,
		"description", "JWT/OIDC authn, RBAC authz, and reverse-proxy edge in front of query-api",
	)

	target, err := url.Parse(queryAPI)
	if err != nil {
		logger.Error("invalid QUERY_API_URL", "err", err)
		os.Exit(1)
	}
	proxy := httputil.NewSingleHostReverseProxy(target)

	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", healthHandler)
	mux.HandleFunc("/v1/", func(w http.ResponseWriter, r *http.Request) {
		// Stub: production validates JWT + injects tenant_id (REQ-F-082 / HLD §9).
		if !authorizeStub(r) {
			writeJSON(w, http.StatusUnauthorized, map[string]string{"error": "unauthorized"})
			return
		}
		// Map public /v1/* → query-api /api/v1/* until OpenAPI paths are unified.
		r.URL.Path = "/api" + r.URL.Path
		proxy.ServeHTTP(w, r)
	})

	logger.Info("listening", "addr", ":"+port)
	if err := http.ListenAndServe(":"+port, mux); err != nil {
		logger.Error("server error", "err", err)
		os.Exit(1)
	}
}

func healthHandler(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{
		"status":  "ok",
		"service": "api-gateway",
		"time":    time.Now().UTC(),
	})
}

// authorizeStub allows all traffic in local/dev unless auth is explicitly required.
func authorizeStub(r *http.Request) bool {
	if os.Getenv("API_GATEWAY_AUTH_REQUIRED") != "true" {
		return true
	}
	return r.Header.Get("Authorization") != ""
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func envOrDefault(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
