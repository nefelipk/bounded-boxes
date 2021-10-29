-------------------------------------------------------------------------
-- Keep cells contained within the each polygon
-------------------------------------------------------------------------

select q.id, cell into table grids 
from (
   select p.id, 
   (ST_Dump(
      mbb_grid_creator(
         ST_GeomFromText(ST_AsText(p.the_geom), 4326))
   )).geom as cell 
   from polygons p 
   group by p.id, p.the_geom
) q, polygons p 
where ST_Within(cell, p.the_geom);