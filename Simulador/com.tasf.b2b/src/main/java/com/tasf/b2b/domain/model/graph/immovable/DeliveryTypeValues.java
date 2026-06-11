package com.tasf.b2b.domain.model.graph.immovable;

import java.util.EnumMap;
import java.util.Map;

public class DeliveryTypeValues {
    private Map<DeliveryType, DeliveryTypeValue> deliveryTypeSettings = new EnumMap<>(DeliveryType.class);

    public DeliveryTypeValues(){
        // Recorremos todos los valores posibles del Enum
        for (DeliveryType type : DeliveryType.values()) {
            try {
                // Intentamos crear el valor por defecto
                deliveryTypeSettings.put(type, new DeliveryTypeValue(type));
            } catch (IllegalArgumentException e) {
                System.err.println("Advertencia: " + e.getMessage());
            }
        }
    }

    public DeliveryTypeValue getDeliveryTypeValue(DeliveryType deliveryType){
        return deliveryTypeSettings.get(deliveryType);
    }
}
