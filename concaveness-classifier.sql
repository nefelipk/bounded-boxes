-------------------------------------------------------------------------
-- Original implementation by Juan Andrés Ramil published at 
-- https://github.com/vamaq/rdbms_playground
-- and https://stackoverflow.com/a/33820952/14413791
-- Altered to calculate the maximum interior angle for each polygon.
-------------------------------------------------------------------------

-- Create segments from points, calculate maximum angle for each line and decide whether it is convex or concave. It works for simple polygons and discards complex polygons with holes. It does not work for intersecting polygons. 

select id, 
       max_value,
into table convex_invekos   -- change to concave_invekos to keep the concave polygons
from (

  select id,
         polygon_num,
         case when vertex = 1
           then max(ang) over (partition by id, polygon_num)
         end as max_value,
         --
         case when max(ang) over (partition by id) < 180
           then true
           else false
         end as isconvex
  from (

    select id,
           polygon_num,
           point_order as vertex,
           -- create the lines connected to the vertex
           case when point_order = 1
             then last_value(ST_Astext(ST_Makeline(sp,ep))) over (partition by id, polygon_num)
             else lag(ST_Astext(ST_Makeline(sp,ep)),1) over (partition by id, polygon_num order by point_order)
           end ||' - '||ST_Astext(ST_Makeline(sp,ep)) as lines,
           --
           degrees(ST_Angle(
           case when point_order = 1
             then last_value(ST_Makeline(sp,ep)) over (partition by id, polygon_num)
             else lag(ST_Makeline(sp,ep),1) over (partition by id, polygon_num order by point_order)
           end, ST_Makeline(sp,ep)
           )) as ang
          from (
          -- 2.- extract the endpoints for every 2-point line segment for each linestring
          --     Group polygons from multipolygon
          select id,
                 coalesce(path[1],0) as polygon_num,
                 generate_series(1, ST_Npoints(geom)-1) as point_order,
                 ST_Pointn(geom, generate_series(1, ST_Npoints(geom)-1)) as sp,
                 ST_Pointn(geom, generate_series(2, ST_Npoints(geom)  )) as ep
          from ( 
          -- 1.- Extract the individual linestrings and the Polygon number for later identification
                 select id,
                        (ST_Dump(ST_Boundary(geom))).geom as geom,
                        (ST_Dump(ST_Boundary(geom))).path as path -- To identify the polygon
                  from invekos ) as pointlist ) as segments ) as max_angles ) as final

-- change to "isconvex = false" to keep the concave polygons
where isconvex = true and polygon_num = 0 and max_value is not null;