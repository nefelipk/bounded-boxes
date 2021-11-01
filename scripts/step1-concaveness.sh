#!/bin/bash

#################################################
# Step 1: Separate convex from concave polygons #
################################################# 

PASSWORD=postgres
DATABASE=gisdb
DATASET=geometries
CONVEX=convex
CONCAVE=concave

echo
echo "Step 1: Separate convex from concave polygons" 

# Q1: Create table $CONVEX with all convex polygons from $DATASET
# Q2: Create table $CONCAVE with all concave polygons from $DATASET

PGPASSWORD=$PASSWORD psql -U postgres -d $DATABASE -c "
select id, 
       geom 
into table "$CONVEX" 
from (

  select id,
         geom,
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
           geom,
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
          from (-- 2.- extract the endpoints for every 2-point line segment for each linestring
          --     Group polygons from multipolygon
          select id,
                 geom,
                 coalesce(path[1],0) as polygon_num,
                 generate_series(1, ST_Npoints(boundary)-1) as point_order,
                 ST_Pointn(boundary, generate_series(1, ST_Npoints(boundary)-1)) as sp,
                 ST_Pointn(boundary, generate_series(2, ST_Npoints(boundary)  )) as ep
          from ( -- 1.- Extract the individual linestrings and the Polygon number for later identification
                 select id,
                        geom,
                        (ST_Dump(ST_Boundary(geom))).geom as boundary,
                        (ST_Dump(ST_Boundary(geom))).path as path -- To identify the polygon
                  from "$DATASET" ) as pointlist ) as segments ) as max_angles ) as final

where isconvex = true and polygon_num = 0 and max_value is not null;"

PGPASSWORD=$PASSWORD psql -U postgres -d $DATABASE -c "
select id, 
       geom 
into table "$CONCAVE" 
from (

  select id,
         geom,
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
           geom,
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
          from (-- 2.- extract the endpoints for every 2-point line segment for each linestring
          --     Group polygons from multipolygon
          select id,
                 geom,
                 coalesce(path[1],0) as polygon_num,
                 generate_series(1, ST_Npoints(boundary)-1) as point_order,
                 ST_Pointn(boundary, generate_series(1, ST_Npoints(boundary)-1)) as sp,
                 ST_Pointn(boundary, generate_series(2, ST_Npoints(boundary)  )) as ep
          from ( -- 1.- Extract the individual linestrings and the Polygon number for later identification
                 select id,
                        geom,
                        (ST_Dump(ST_Boundary(geom))).geom as boundary,
                        (ST_Dump(ST_Boundary(geom))).path as path -- To identify the polygon
                  from "$DATASET" ) as pointlist ) as segments ) as max_angles ) as final

where isconvex = false and polygon_num = 0 and max_value is not null;"

echo