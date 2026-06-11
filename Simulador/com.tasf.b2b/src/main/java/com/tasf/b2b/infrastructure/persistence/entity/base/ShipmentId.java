package com.tasf.b2b.infrastructure.persistence.entity.base;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ShipmentId implements Serializable {

    @Column(length = 64)
    private String id;

    @Column(name = "origin_icao", length = 4)
    private String originIcao;

    public ShipmentId() {}

    public ShipmentId(String id, String originIcao) {
        this.id         = id;
        this.originIcao = originIcao;
    }

    public String getId()         { return id; }
    public String getOriginIcao() { return originIcao; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShipmentId s)) return false;
        return Objects.equals(id, s.id) && Objects.equals(originIcao, s.originIcao);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, originIcao);
    }
}
