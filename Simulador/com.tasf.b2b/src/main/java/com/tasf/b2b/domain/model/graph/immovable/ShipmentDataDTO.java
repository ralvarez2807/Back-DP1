// ShipmentData.java
/* Dato fijo de un vuelo real
* Constructor
* Setters
* Getters
* Impresión del vuelo real
* */
package com.tasf.b2b.domain.model.graph.immovable;

import java.time.Instant;

public class ShipmentDataDTO {
    private final String         id;
    private final Instant        entryDateTimeUtc; //Tiempo de llegada del paquete inicial en Utc
    private final Instant        deadlineDateTimeUtc; //Tiempo en el que debería llegar a su destino
    private final AirportDataDTO originAirport;
    private final AirportDataDTO destAirport;
    private final int            quantity;
    private final String         clientId;
    private final DeliveryTypeValue   typeValue;

    //Constructor
    public ShipmentDataDTO(String id, Instant entryDateTimeUtc,
                           AirportDataDTO originAirport,
                           AirportDataDTO destAirport, int quantity, String clientId,
                           DeliveryTypeValues deliveryTypeValues) {
        this.id             = id;
        this.entryDateTimeUtc = entryDateTimeUtc;
        this.originAirport = originAirport;
        this.destAirport       = destAirport;
        this.quantity       = quantity;
        this.clientId       = clientId;
        this.typeValue = originAirport.deliveryTypeTo(destAirport, deliveryTypeValues);
        this.deadlineDateTimeUtc = this.calculateDeadlineTime(entryDateTimeUtc, this.typeValue);
    }

    //Getters
    public String getId() {
        return id;
    }

    public Instant getEntryDateTimeUtc() {
        return entryDateTimeUtc;
    }

    public Instant getDeadlineDateTimeUtc() {
        return deadlineDateTimeUtc;
    }

    public AirportDataDTO getOriginAirport() {
        return originAirport;
    }

    public AirportDataDTO getDestAirport() {
        return destAirport;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getClientId() {
        return clientId;
    }

    public DeliveryTypeValue getTypeValue() {
        return typeValue;
    }

    //Calcular el tiempo límite de la maleta
    public Instant calculateDeadlineTime(Instant entryDateTimeUtc, DeliveryTypeValue deliveryTypeValue){
        return entryDateTimeUtc.plus(deliveryTypeValue.getMaxDeliveryTime());
    }

    //Para imprimir
    @Override
    public String toString() {
        return String.format(
                "Shipment[%s | %s→%s | qty=%d | entryUTC=%s]",
                id, originAirport.getIcao(), destAirport.getIcao(),
                quantity, entryDateTimeUtc);
    }
}