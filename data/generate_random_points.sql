-- Code from https://bytes.yingw787.com/posts/2019/01/10/generating_randomized_postgis_data/
------------------------------------------------------------------------

-- Spin up or register the PostgreSQL PostGIS extension within the PostgreSQL
-- database if you have not done so already. Skip if done.
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE OR REPLACE FUNCTION generate_random_point()
    RETURNS GEOMETRY AS
$func$
DECLARE
    -- x is variation in longitude, while y is variation in latitude.
    -- In order to generate quality test data, limit the scope to the surface
    -- of the world: -90/90 from North to South poles, and -180/180 for
    -- wrap-around coverage. Thanks to coworkers for this information.
    x_min INTEGER := -90;
    x_max INTEGER := 90;
    y_min INTEGER := -180;
    y_max INTEGER := 180;
    -- Use the WGS84 coordinate system, referenced by spatial reference ID
    -- (SRID) 4326.
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

CREATE OR REPLACE FUNCTION generate_random_points()
    RETURNS VOID AS
$func$
DECLARE
    -- Change this as needed to suit your test dataset size requirements.
    num_records INTEGER := 1000;
BEGIN
    -- Hardcode a seed to generate reproducible results.
    SET seed TO 0.5;

    -- Prepending a column to an existing table using ALTER TABLE is not
    -- possible. Store the data in a temporary table, then generate the final
    -- table from the temporary table.
    DROP TABLE IF EXISTS temp_postgis_points_random;
    -- Name column 'geom_points', as otherwise default column name will be
    -- the name of the function, i.e. 'generate_random_point'.
    CREATE TABLE temp_postgis_points_random (geom_points)
    AS SELECT generate_random_point();

    -- As table schema is created from function generate_random_point() and
    -- populated with one record already, loop num_records - 1 to create a
    -- total of num_records records.
    FOR idx IN 1 .. num_records - 1 LOOP
        INSERT INTO temp_postgis_points_random SELECT generate_random_point();
    END LOOP;

    DROP TABLE IF EXISTS postgis_points_random;
    CREATE TABLE postgis_points_random (
        record_id SERIAL PRIMARY KEY,
        geom_points GEOMETRY(POINT, 4326)
    );
    INSERT INTO postgis_points_random (geom_points)
    SELECT geom_points FROM temp_postgis_points_random;

    DROP TABLE temp_postgis_points_random;
END;
$func$
LANGUAGE 'plpgsql' VOLATILE;

SELECT generate_random_points();
