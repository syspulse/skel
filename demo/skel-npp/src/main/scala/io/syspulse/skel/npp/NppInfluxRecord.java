package io.syspulse.skel.npp;

import java.time.Instant;
import javax.annotation.Nullable;
import com.influxdb.annotations.Column;
import com.influxdb.annotations.Measurement;

@Measurement(name = "npp")
public class NppInfluxRecord {

    // Name must be ALWAYS "time" to capture timestamp!
    @Column(name = "time", timestamp = true)
    Instant ts;

    @Column(name = "area", tag = true)
    String area;

    @Column(name = "geohash", tag = true)
    String geohash;

    @Column(name = "dose")
    Double dose;

    public NppInfluxRecord() {
    }

    NppInfluxRecord(@Nullable final Long ts, String area, String goehash, Double dose) {
        this(ts != null ? Instant.ofEpochMilli(ts) : null, area, goehash, dose);
    }

    NppInfluxRecord(@Nullable final Instant ts, String area, String geohash, Double dose) {
        this.ts = ts;
        this.area = area;
        this.geohash = geohash;
        this.dose = dose;
    }
}