package com.legalsuite.mail;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Pay-what-you-use unit prices for SMS and WhatsApp. A blank rate is treated as zero, not a free bundle. */
@Component
@ConfigurationProperties(prefix = "legalsuite.messaging")
public class MessagingProperties {
    private BigDecimal smsUnitCost = new BigDecimal("0.08");
    private BigDecimal whatsappUnitCost = new BigDecimal("0.05");

    public BigDecimal getSmsUnitCost() { return smsUnitCost == null ? BigDecimal.ZERO : smsUnitCost; }
    public void setSmsUnitCost(BigDecimal smsUnitCost) { this.smsUnitCost = smsUnitCost; }
    public BigDecimal getWhatsappUnitCost() { return whatsappUnitCost == null ? BigDecimal.ZERO : whatsappUnitCost; }
    public void setWhatsappUnitCost(BigDecimal whatsappUnitCost) { this.whatsappUnitCost = whatsappUnitCost; }
}
