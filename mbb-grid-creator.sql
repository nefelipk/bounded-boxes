-------------------------------------------------------------------------
-- Original implementation by Matt published at
-- https://gis.stackexchange.com/a/257485
-- Altered to calculate the axis-aligned grid with fixed granularity.
-------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.mbb_grid_creator (
  bound_polygon public.geometry
)
RETURNS public.geometry AS
$body$
DECLARE
  Xmin DOUBLE PRECISION;
  Xmax DOUBLE PRECISION;
  Ymax DOUBLE PRECISION;
  Ymin DOUBLE PRECISION;
  X DOUBLE PRECISION;
  Y DOUBLE PRECISION;
  sectors public.geometry[];
  i INTEGER;
  SRID INTEGER;
  step DOUBLE PRECISION;
BEGIN
  Xmin := ST_XMin($1);
  Xmax := ST_XMax($1);
  Ymax := ST_YMax($1);
  Ymin := ST_YMin($1);
  SRID := ST_SRID($1);

  IF ((Xmax - Xmin) > (Ymax - Ymin)) THEN
      step := (Xmax - Xmin) / 10;
  ELSE
      step := (Ymax - Ymin) / 10;
  END IF;

  Y := Ymin; --current sector's corner coordinate
  i := -1;
  <<yloop>>
  LOOP
    IF (Y > Ymax) THEN  
        EXIT;
    END IF;

    X := Xmin;
    <<xloop>>
    LOOP
      IF (X > Xmax) THEN
          EXIT;
      END IF;

      i := i + 1;
      sectors[i] := ST_MakeEnvelope(X, Y, (X + step), (Y + step), SRID);

      X := X + step;
    END LOOP xloop;
    Y := Y + step;
  END LOOP yloop;

  RETURN ST_Collect(sectors);
END;
$body$
LANGUAGE 'plpgsql';