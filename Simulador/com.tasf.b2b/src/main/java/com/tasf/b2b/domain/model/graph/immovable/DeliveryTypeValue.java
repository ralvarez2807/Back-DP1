package com.tasf.b2b.domain.model.graph.immovable;

import java.time.Duration;

public class DeliveryTypeValue {
    private DeliveryType deliveryType;
    private Duration maxDeliveryTime;
    private static final Duration MAXDELIVERYTIMEINTRACONTINENTAL = Duration.ofHours(24);
    private static final Duration MAXDELIVERYTIMEINTERCONTINENTAL = Duration.ofHours(48);


    private DeliveryTypeValue(DeliveryType deliveryType, Duration maxDeliveryTime) {
        this.deliveryType = deliveryType;
        this.maxDeliveryTime = maxDeliveryTime;
    }

    public DeliveryTypeValue(DeliveryType deliveryType){
        if(deliveryType == DeliveryType.INTRACONTINENTAL){
            this.maxDeliveryTime = MAXDELIVERYTIMEINTRACONTINENTAL;
        }
        else if(deliveryType == DeliveryType.INTERCONTINENTAL){
            this.maxDeliveryTime = MAXDELIVERYTIMEINTERCONTINENTAL;
        }
        else{
            throw new IllegalArgumentException(
                    "Tipo de viaje sin maximo tiempo por defecto: " + this.deliveryType.toString());
        }
    }

    public Duration getMaxDeliveryTime() {
        return maxDeliveryTime;
    }
}
