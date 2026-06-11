package com.tasf.b2b.infrastructure.persistence.entity.reference;

import jakarta.persistence.*;

@Entity
@Table(schema = "reference", name = "airports")
public class AirportEntity {

    @Id
    @Column(length = 4)
    private String icao;

    @Column(nullable = false)
    private String city;

    private String country;

    @Column(nullable = false)
    private String continent;

    @Column(name = "short_name")
    private String shortName;

    @Column(name = "gmt_offset", nullable = false)
    private short gmtOffset;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    public AirportEntity() {}

    public AirportEntity(String icao, String city, String country, String continent,
                         String shortName, short gmtOffset, int capacity,
                         double latitude, double longitude) {
        this.icao      = icao;
        this.city      = city;
        this.country   = country;
        this.continent = continent;
        this.shortName = shortName;
        this.gmtOffset = gmtOffset;
        this.capacity  = capacity;
        this.latitude  = latitude;
        this.longitude = longitude;
    }

    public String getIcao()      { return icao; }
    public String getCity()      { return city; }
    public String getCountry()   { return country; }
    public String getContinent() { return continent; }
    public String getShortName() { return shortName; }
    public short  getGmtOffset() { return gmtOffset; }
    public int    getCapacity()  { return capacity; }
    public double getLatitude()  { return latitude; }
    public double getLongitude() { return longitude; }
}
