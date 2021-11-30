-- Code from https://bytes.yingw787.com/posts/2019/01/10/generating_randomized_postgis_data/
------------------------------------------------------------------------

CREATE EXTENSION IF NOT EXISTS postgis;

-- Function mostly copied from generate_random_point().
CREATE OR REPLACE FUNCTION generate_random_quasicentroid()
    RETURNS geometry AS
$func$
DECLARE
    -- Points are declared between -70/70 degrees on the Y-axis, and -180/180
    -- degrees on the X-axis, to allow room for creation of polygons and
    -- rendering on WMS (avoid cutoff in polar areas).
    x_min INTEGER := -180;
    x_max INTEGER := 180;
    y_min INTEGER := -70;
    y_max INTEGER := 70;
    srid INTEGER := 4326;
BEGIN
    RETURN (
        ST_SetSRID(
            ST_MakePoint(
                random()*(x_max - x_min) + x_min,
                random()*(y_max - y_min) + y_min
            ),
            srid
        )
    );
END;
$func$
LANGUAGE 'plpgsql' VOLATILE;

-- This function creates a polygon from a set of randomly defined angles, and
-- randomly defined distances from a quasicentroid along that angle.
CREATE OR REPLACE FUNCTION generate_random_polygon(
    quasicentroid geometry
)
    RETURNS geometry AS
$func$
DECLARE
    idx INTEGER;
    nodes geometry[];
    angle FLOAT;
    starting_angle FLOAT;
    distance FLOAT;
    max_distance FLOAT := 1;
BEGIN
    -- NOTE: Do NOT wrap parentheses around fraction! 'plpgsql' will not
    -- understand this query and will round down to 0.
    starting_angle := random()*1/3*PI();
    angle := starting_angle;
    -- Set a maximum of 20 vertices for a polygon
    FOR idx IN 1 .. 20 LOOP
        distance := random() * max_distance;
        SELECT array_append(
            nodes,
            ST_Translate(
                quasicentroid,
                sin(angle)*distance,
                cos(angle)*distance
            )
        ) INTO nodes;
        -- NOTE: Do NOT wrap parentheses around fraction! 'plpgsql' will not
        -- understand the query and will round down to 0.
        angle := angle + random()*2/3*PI();
        IF angle > 2 * PI() THEN EXIT; END IF;
    END LOOP;
    -- Close the polygon
    SELECT array_append(
        nodes,
        nodes[1]
    ) INTO nodes;
    RETURN ST_MakePolygon(
        ST_MakeLine(
            nodes
        )
    );
END;
$func$
LANGUAGE 'plpgsql' VOLATILE;

CREATE OR REPLACE FUNCTION generate_random_polygons()
    RETURNS void AS
$func$
DECLARE
    num_records INTEGER := 1000;
    quasicentroid geometry;
BEGIN
    -- NOTE: Setting a seed to generate reproducible results.
    SET seed TO 0.5;
    DROP TABLE IF EXISTS temp_postgis_polygons_random;
    -- Table name is 'temp_postgis_polygons_random'.
    -- Column name containing PostGIS data is called 'geom_polygons'.
    quasicentroid := generate_random_quasicentroid();
    CREATE TABLE temp_postgis_polygons_random (geom_polygons)
    AS SELECT generate_random_polygon(quasicentroid);

    -- Populate the table with number of records denoted in 'num_records'.
    FOR idx IN 1 .. num_records - 1 LOOP
        quasicentroid := generate_random_quasicentroid();
        INSERT INTO temp_postgis_polygons_random
        SELECT generate_random_polygon(quasicentroid);
    END LOOP;

    -- Create final table with column definitions.
    DROP TABLE IF EXISTS postgis_polygons_random;
    CREATE TABLE postgis_polygons_random (
        record_id SERIAL PRIMARY KEY,
        geom_polygons GEOMETRY(POLYGON, 4326)
    );
    INSERT INTO postgis_polygons_random (geom_polygons)
    SELECT geom_polygons FROM temp_postgis_polygons_random;

    DROP TABLE temp_postgis_polygons_random;
END;
$func$
LANGUAGE 'plpgsql' VOLATILE;

SELECT generate_random_polygons();