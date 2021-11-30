-- Code from https://bytes.yingw787.com/posts/2019/01/10/generating_randomized_postgis_data/
------------------------------------------------------------------------

CREATE EXTENSION IF NOT EXISTS postgis;

-- This function is copied over from 'postgis_create_points.sql'.
CREATE OR REPLACE FUNCTION generate_random_point()
    RETURNS geometry AS
$func$
DECLARE
    -- Points should be declared between -90/90 degrees on the Y-axis, and
    -- -180/180 degrees on the X-axis, in order to render WMS representations
    -- correctly.
    x_min INTEGER := -180;
    x_max INTEGER := 180;
    y_min INTEGER := -90;
    y_max INTEGER := 90;
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

-- This function creates a linestring out of a randomly defined set of POINT
-- geometries.
CREATE OR REPLACE FUNCTION generate_random_linestring()
    RETURNS geometry AS
$func$
DECLARE
    nodes geometry[];
    minimum_number_of_nodes INTEGER := 2;
    maximum_number_of_nodes INTEGER := 20;
    number_of_nodes INTEGER;
BEGIN
    number_of_nodes = (
        random()*(maximum_number_of_nodes-minimum_number_of_nodes
    ) + minimum_number_of_nodes)::INTEGER;
    FOR idx IN 1 .. number_of_nodes LOOP
        SELECT array_append(
            nodes,
            generate_random_point()
        ) INTO nodes;
    END LOOP;
    -- Close the polygon
    RETURN ST_MakeLine(
        nodes
    );
END;
$func$
LANGUAGE 'plpgsql' VOLATILE;

CREATE OR REPLACE FUNCTION generate_random_linestrings()
    RETURNS void AS
$func$
DECLARE
    num_records INTEGER := 1000;
BEGIN
    -- NOTE: Setting a seed to generate reproducible results.
    SET seed TO 0.5;
    -- Generate a temporary table to store geometry data.
    DROP TABLE IF EXISTS temp_postgis_linestrings_random;
    -- Column name containing PostGIS data is called 'geom_linestrings'.
    CREATE TABLE temp_postgis_linestrings_random (geom_linestrings)
    AS SELECT generate_random_linestring();

    -- Populate the table with number of records denoted in 'num_records'.
    FOR idx IN 1 .. num_records - 1 LOOP
        INSERT INTO temp_postgis_linestrings_random
        SELECT generate_random_linestring();
    END LOOP;

    -- Create final table with column definitions.
    DROP TABLE IF EXISTS postgis_linestrings_random;
    CREATE TABLE postgis_linestrings_random (
        record_id SERIAL PRIMARY KEY,
        geom_linestrings GEOMETRY(LINESTRING, 4326)
    );
    INSERT INTO postgis_linestrings_random (geom_linestrings)
    SELECT geom_linestrings FROM temp_postgis_linestrings_random;

    DROP TABLE temp_postgis_linestrings_random;
END;
$func$
LANGUAGE 'plpgsql' VOLATILE;

SELECT generate_random_linestrings();