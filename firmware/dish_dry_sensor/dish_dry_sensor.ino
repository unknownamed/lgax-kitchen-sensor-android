#include <WiFi.h>
#include <WebServer.h>
#include <DHT.h>

const uint8_t DHT_PIN = 4;
const uint8_t LIGHT_PIN = 34;
const unsigned long SAMPLE_INTERVAL_MS = 3000;
DHT dht(DHT_PIN, DHT22);
WebServer server(80);
float temperatureC = NAN;
float humidityPercent = NAN;
int lightAdc = 0;
unsigned long measuredAt = 0;
unsigned long lastAttempt = 0;
bool validReading = false;

void measure() {
  lastAttempt = millis();
  float t = dht.readTemperature();
  float h = dht.readHumidity();
  validReading = !isnan(t) && !isnan(h) && t >= -40 && t <= 80 && h >= 0 && h <= 100;
  if (!validReading) return;
  temperatureC = t;
  humidityPercent = h;
  lightAdc = analogRead(LIGHT_PIN);
  measuredAt = millis();
}

void sendReading() {
  server.sendHeader("Cache-Control", "no-store");
  if (!validReading || millis() - measuredAt > 10000) {
    server.send(503, "application/json", "{\"error\":\"sensor_unavailable\"}");
    return;
  }
  String body = "{\"device\":\"dishdry-esp32\",\"temperature_c\":";
  body += String(temperatureC, 1);
  body += ",\"humidity_percent\":" + String(humidityPercent, 1);
  body += ",\"light_adc\":" + String(lightAdc);
  body += ",\"sample_age_ms\":" + String(millis() - measuredAt) + "}";
  server.send(200, "application/json", body);
}

void setup() {
  Serial.begin(115200);
  dht.begin();
  analogReadResolution(12);
  WiFi.mode(WIFI_AP);
  // A dedicated development hotspot, not a home Wi-Fi credential.
  if (!WiFi.softAP("DishDrySensor-ESP32", "dishdry123")) {
    Serial.println("Hotspot could not start");
    return;
  }
  Serial.print("Sensor endpoint: http://");
  Serial.print(WiFi.softAPIP());
  Serial.println("/readings");
  server.on("/readings", HTTP_GET, sendReading);
  server.onNotFound([]() { server.send(404, "text/plain", "Not found"); });
  server.begin();
}

void loop() {
  if (millis() - lastAttempt >= SAMPLE_INTERVAL_MS) measure();
  server.handleClient();
  delay(2);
}
