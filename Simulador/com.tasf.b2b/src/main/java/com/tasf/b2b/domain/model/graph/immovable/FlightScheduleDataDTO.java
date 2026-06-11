// FlightScheduleData.java
/* Donde se guardan los datos fijos de la programación de un vuelo
* Constructor
* Getters
* Generar el id de la programción del vuelo
* Impresión de la programación del vuelo
* */
package com.tasf.b2b.domain.model.graph.immovable;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class FlightScheduleDataDTO {
    private final String id;
    private final AirportDataDTO originAirport;
    private final AirportDataDTO destAirport;
    private final LocalTime     departureTimeLocal; //Tiempo definido de forma diaria en local
    private final LocalTime     arrivalTimeLocal; //Tiempo definido de forma diaria en local
    private final int           capacity;

    //Constructor
    public FlightScheduleDataDTO(AirportDataDTO originAirport, AirportDataDTO destAirport,
                                 LocalTime departureTimeLocal, LocalTime arrivalTimeLocal,
                                 int capacity) {
        this.id = this.generateIdFlightSchedule(originAirport.getIcao(), destAirport.getIcao(), departureTimeLocal);
        this.originAirport = originAirport;
        this.destAirport = destAirport;
        this.departureTimeLocal = departureTimeLocal;
        this.arrivalTimeLocal   = arrivalTimeLocal;
        this.capacity     = capacity;
    }

    //Getters
    public String getId() {
        return id;
    }

    public int getCapacity() {
        return capacity;
    }

    public LocalTime getArrivalTimeLocal() {
        return arrivalTimeLocal;
    }

    public LocalTime getDepartureTimeLocal() {
        return departureTimeLocal;
    }

    public AirportDataDTO getDestAirport() {
        return destAirport;
    }

    public AirportDataDTO getOriginAirport() {
        return originAirport;
    }

    //Generar el id de la programación del vuelo
    public String generateIdFlightSchedule(String departureAirportIcao, String arrivalAirportIcao,
                                           LocalTime departureTimeLocal){
        //Formateador
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        return departureAirportIcao + "-" + arrivalAirportIcao + "-" + departureTimeLocal.format(formatter);

    }

    //Para imprimir la programación del vuelo
    @Override
    public String toString() {
        return String.format("%s→%s | dep=%s arr=%s | cap=%d]",
                originAirport.getIcao(), destAirport.getIcao(),
                departureTimeLocal, arrivalTimeLocal, capacity);
    }
}