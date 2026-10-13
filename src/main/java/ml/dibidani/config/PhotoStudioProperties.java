package ml.dibidani.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.photo-studio")
public class PhotoStudioProperties {
  private String engine = "local";
  private String serviceUrl = "http://127.0.0.1:8090";
  private String token = "";
  private long maxBytes = 12_582_912;
  private int quotaPerUser = 200;
  private boolean supplierLookupEnabled = false;

  public String getEngine() { return engine; }
  public void setEngine(String v) { engine = v; }
  public String getServiceUrl() { return serviceUrl; }
  public void setServiceUrl(String v) { serviceUrl = v; }
  public String getToken() { return token; }
  public void setToken(String v) { token = v; }
  public long getMaxBytes() { return maxBytes; }
  public void setMaxBytes(long v) { maxBytes = v; }
  public int getQuotaPerUser() { return quotaPerUser; }
  public void setQuotaPerUser(int v) { quotaPerUser = v; }
  public boolean isSupplierLookupEnabled() { return supplierLookupEnabled; }
  public void setSupplierLookupEnabled(boolean v) { supplierLookupEnabled = v; }
}
