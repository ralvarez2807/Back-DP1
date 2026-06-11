// AirportDataDTO.java
/* Donde se guardan los datos fijos de un aeropuerto
* ¿Qué hay?
* Constructor
* Getters
* Funciones para transformar [latitud | longitud] a String o viceversa (se usa afuera)
* Impresión del aeropuerto
* */
package com.tasf.b2b.domain.model.graph.immovable;

import java.util.Objects;

public class AirportDataDTO {
    // Ejemplo para Bogota
    private final String icao; // SKBO para Bogota
    private final String city; // Bogota
    private final String country; // Colombia
    private final String continent;
    private final String short_name; //bogo
    private final int    gmtOffset; //-5
    private final int    capacity; // 430
    private final double latitude; // 04° 42' 05" N
    private final double longitude; // 74° 08' 49" W

    //Constructor
    public AirportDataDTO(String icao, String city, String country, String continent, String short_name,
                          int gmtOffset, int capacity, double latitude, double longitude) {
        this.longitude = longitude;
        this.latitude = latitude;
        this.capacity = capacity;
        this.gmtOffset = gmtOffset;
        this.short_name = short_name;
        this.country = country;
        this.city = city;
        this.icao = icao;
        this.continent = continent;
    }

    //Getters
    public String getIcao() {
        return icao;
    }

    public String getCity() {
        return city;
    }

    public String getCountry() {
        return country;
    }

    public String getContinent() {
        return continent;
    }

    public String getShort_name() {
        return short_name;
    }

    public int getGmtOffset() {
        return gmtOffset;
    }

    public int getCapacity() {
        return capacity;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    //Conversión de [latitud o longitud] a decimales
    public static double fromGMSToDecimal(int deg, int min, double sec, char direction) {
        double decimal = deg + (min / 60.0) + (sec / 3600.0);
        if (direction == 'S' || direction == 'W') {
            decimal *= -1;
        }
        return decimal;
    }

    //Conversión de decimales a [latitud o longitud]
    public static String decimalToDMS(double coordinate, boolean isLatitude) {
        // Determinamos la dirección
        String direction;
        if (isLatitude) {
            direction = (coordinate >= 0) ? "N" : "S";
        } else {
            direction = (coordinate >= 0) ? "E" : "W";
        }

        // Trabajamos con el valor absoluto para los cálculos
        double absCoord = Math.abs(coordinate);

        // 1. Grados
        int degrees = (int) absCoord;

        // 2. Minutos
        double remainderMinutes = (absCoord - degrees) * 60;
        int minutes = (int) remainderMinutes;

        // 3. Segundos
        double seconds = (remainderMinutes - minutes) * 60;

        // Retornamos un String formateado
        return String.format("%02d° %02d' %.2f\" %s", degrees, minutes, seconds, direction);
    }

    //Calcular si son intercontinentales o intracontinentales dos aeropuertos
    public DeliveryTypeValue deliveryTypeTo(AirportDataDTO other, DeliveryTypeValues deliveryTypeValues) {
        return (Objects.equals(this.continent, other.getContinent())) //Objects.equals falla si hay un null
                ? deliveryTypeValues.getDeliveryTypeValue(DeliveryType.INTRACONTINENTAL)
                : deliveryTypeValues.getDeliveryTypeValue(DeliveryType.INTERCONTINENTAL);
    }

    // Para impresión completa
    @Override
    public String toString() {
        return icao + " (" + city + ", GMT" + (gmtOffset >= 0 ? "+" : "") + gmtOffset + ")";
    }
}