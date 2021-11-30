
CREATE EXTENSION IF NOT EXISTS postgis;

DROP FUNCTION rotate_mbb_polygons();

CREATE OR REPLACE FUNCTION rotate_mbb_polygons()
    RETURNS void AS
$func$
DECLARE
    angle FLOAT;
    idx INTEGER;
BEGIN
    
    CREATE TABLE random_rotated_rectangles (
        id SERIAL PRIMARY KEY,
        geom GEOMETRY(POLYGON, 4326)
    );

    FOR idx IN SELECT * FROM random_polygons LOOP
        angle := random()*PI();
        INSERT INTO random_rotated_rectangles
        SELECT id, ST_Rotate(ST_Envelope(geom), angle)
        FROM random_polygons
        WHERE id = idx;
    END LOOP;
END;
$func$
LANGUAGE 'plpgsql' VOLATILE;

SELECT rotate_mbb_polygons();
