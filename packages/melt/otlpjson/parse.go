package otlpjson

import (
	"encoding/json"
	"fmt"
)

// attrMap extracts string attributes from OTLP JSON attribute arrays.
func attrMap(attrs []any) map[string]string {
	out := map[string]string{}
	for _, a := range attrs {
		m, ok := a.(map[string]any)
		if !ok {
			continue
		}
		key, _ := m["key"].(string)
		val, _ := m["value"].(map[string]any)
		if key == "" || val == nil {
			continue
		}
		if s, ok := val["stringValue"].(string); ok {
			out[key] = s
		}
	}
	return out
}

// ExtractResourceMeta pulls service.name / deployment.environment from an OTLP resource object.
func ExtractResourceMeta(resource map[string]any) (service, env string, labels map[string]string) {
	labels = map[string]string{}
	if resource == nil {
		return "unknown", "unknown", labels
	}
	rawAttrs, _ := resource["attributes"].([]any)
	labels = attrMap(rawAttrs)
	service = labels["service.name"]
	if service == "" {
		service = "unknown"
	}
	env = labels["deployment.environment"]
	if env == "" {
		env = labels["deployment.environment.name"]
	}
	if env == "" {
		env = "unknown"
	}
	return service, env, labels
}

// FirstTraceIDs returns the first span's trace/span ids from an OTLP traces JSON body.
func FirstTraceIDs(body []byte) (traceID, spanID string, err error) {
	var root map[string]any
	if err := json.Unmarshal(body, &root); err != nil {
		return "", "", fmt.Errorf("otlp traces json: %w", err)
	}
	rss, _ := root["resourceSpans"].([]any)
	for _, rs := range rss {
		rsm, _ := rs.(map[string]any)
		sss, _ := rsm["scopeSpans"].([]any)
		for _, ss := range sss {
			ssm, _ := ss.(map[string]any)
			spans, _ := ssm["spans"].([]any)
			for _, sp := range spans {
				spm, _ := sp.(map[string]any)
				tid, _ := spm["traceId"].(string)
				sid, _ := spm["spanId"].(string)
				return tid, sid, nil
			}
		}
	}
	return "", "", nil
}

// FirstResourceFromTraces extracts resource from first resourceSpans entry.
func FirstResourceFromTraces(body []byte) (service, env string, labels map[string]string, err error) {
	var root map[string]any
	if err := json.Unmarshal(body, &root); err != nil {
		return "", "", nil, err
	}
	rss, _ := root["resourceSpans"].([]any)
	if len(rss) == 0 {
		return "unknown", "unknown", map[string]string{}, nil
	}
	rsm, _ := rss[0].(map[string]any)
	res, _ := rsm["resource"].(map[string]any)
	service, env, labels = ExtractResourceMeta(res)
	return service, env, labels, nil
}

// FirstResourceFromMetrics extracts resource from first resourceMetrics entry.
func FirstResourceFromMetrics(body []byte) (service, env string, labels map[string]string, err error) {
	var root map[string]any
	if err := json.Unmarshal(body, &root); err != nil {
		return "", "", nil, err
	}
	rms, _ := root["resourceMetrics"].([]any)
	if len(rms) == 0 {
		return "unknown", "unknown", map[string]string{}, nil
	}
	rm, _ := rms[0].(map[string]any)
	res, _ := rm["resource"].(map[string]any)
	service, env, labels = ExtractResourceMeta(res)
	return service, env, labels, nil
}

// FirstResourceFromLogs extracts resource from first resourceLogs entry.
func FirstResourceFromLogs(body []byte) (service, env string, labels map[string]string, err error) {
	var root map[string]any
	if err := json.Unmarshal(body, &root); err != nil {
		return "", "", nil, err
	}
	rls, _ := root["resourceLogs"].([]any)
	if len(rls) == 0 {
		return "unknown", "unknown", map[string]string{}, nil
	}
	rl, _ := rls[0].(map[string]any)
	res, _ := rl["resource"].(map[string]any)
	service, env, labels = ExtractResourceMeta(res)
	return service, env, labels, nil
}
