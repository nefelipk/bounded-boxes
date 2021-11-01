#!/bin/bash

######################################################
# Step 2: Create inner grid for each polygon with 	 #
#         at least 4 and at most 12 contiguous cells #
######################################################

PASSWORD=postgres
DATABASE=gisdb
CONVEX=convex
CONCAVE=concave
CONVEX_GRID="$CONVEX"_grid
CONCAVE_GRID="$CONCAVE"_grid
LOWER_LIMIT=4
UPPER_LIMIT=12

echo
echo "Step 2: Create inner grid for each polygon with at least "$LOWER_LIMIT" and at most "$UPPER_LIMIT" cells"

# Use mbb_grid_creator to create a grid of the mbb of each polygon and keep only the inner cells
echo "Keep only inner cells with mbb_grid_creator"

PGPASSWORD=$PASSWORD psql -U postgres -d $DATABASE -c "CREATE TABLE "$CONVEX_GRID" (id integer, cell geometry(Polygon, 4326)); 
	INSERT INTO "$CONVEX_GRID" SELECT q.id, cell FROM (SELECT id, (ST_Dump(mbb_grid_creator( geom ))).geom AS cell FROM "$CONVEX" GROUP BY id, geom)q, "$CONVEX" p WHERE q.id = p.id AND ST_Within(cell, p.geom);"

PGPASSWORD=$PASSWORD psql -U postgres -d $DATABASE -c "CREATE TABLE "$CONCAVE_GRID" (id integer, cell geometry(Polygon, 4326)); 
	INSERT INTO "$CONCAVE_GRID" SELECT q.id, cell FROM (SELECT id, (ST_Dump(mbb_grid_creator( geom ))).geom AS cell FROM "$CONCAVE" GROUP BY id, geom)q, "$CONCAVE" p WHERE q.id = p.id AND ST_Within(cell, p.geom);"


# Delete polygon registrations when their inner cells do not share at least one point with each other
echo
echo "Delete polygons with fragmented cells"

PGPASSWORD=$PASSWORD psql -U postgres -d $DATABASE -c "DELETE FROM "$CONVEX_GRID" 
	WHERE id IN (SELECT sq.id
	        FROM (SELECT id,
	                     case when count(dump) > 1
	                       then true
	                       else false
	                     end AS multipoly
	              FROM (SELECT id, (ST_Dump(poly)).geom AS dump
	                    FROM (SELECT id, ST_Union(cell) AS poly 
	                          FROM "$CONVEX_GRID" 
	                          GROUP BY id) sq3 
	                    GROUP BY id, poly) sq2
	              GROUP BY id) sq
	        WHERE multipoly = true
	        GROUP BY sq.id);"

PGPASSWORD=$PASSWORD psql -U postgres -d $DATABASE -c "DELETE FROM "$CONCAVE_GRID" 
	WHERE id IN (SELECT sq.id
	        FROM (SELECT id,
	                     case when count(dump) > 1
	                       then true
	                       else false
	                     end AS multipoly
	              FROM (SELECT id, (ST_Dump(poly)).geom AS dump
	                    FROM (SELECT id, ST_Union(cell) AS poly 
	                          FROM "$CONCAVE_GRID" 
	                          GROUP BY id) sq3 
	                    GROUP BY id, poly) sq2
	              GROUP BY id) sq
	        WHERE multipoly = true
	        GROUP BY sq.id);"


# Delete polygon registrations when their inner cells are less than $LOWER_LIMIT or more than $UPPER_LIMIT
echo
echo "Delete polygons with too few or too many inner cells"

PGPASSWORD=$PASSWORD psql -U postgres -d $DATABASE -c "DELETE FROM "$CONVEX_GRID"
	WHERE id IN (SELECT id
	             FROM (SELECT id, count(*) AS count_poly
	                   FROM "$CONVEX_GRID" 
	                   GROUP BY id) sq
	             WHERE count_poly < "$LOWER_LIMIT" OR count_poly > "$UPPER_LIMIT");"

PGPASSWORD=$PASSWORD psql -U postgres -d $DATABASE -c "DELETE FROM "$CONCAVE_GRID"
	WHERE id IN (SELECT id
	             FROM (SELECT id, count(*) AS count_poly
	                   FROM "$CONCAVE_GRID" 
	                   GROUP BY id) sq
	             WHERE count_poly < "$LOWER_LIMIT" OR count_poly > "$UPPER_LIMIT");"


# Print id, cell, ST_AsText(cell) from $CONVEX_GRID and $CONCAVE_GRID to csv files
echo
echo "Print inner cells of remaining polygons to files"
echo

PGPASSWORD=$PASSWORD psql -U postgres -d $DATABASE -c "SELECT id, cell, ST_AsText(cell)
	FROM "$CONVEX_GRID" ORDER BY id ASC;" > $CONVEX_GRID.csv

PGPASSWORD=$PASSWORD psql -U postgres -d $DATABASE -c "SELECT id, cell, ST_AsText(cell)
  FROM "$CONCAVE_GRID" ORDER BY id ASC;" > $CONCAVE_GRID.csv
