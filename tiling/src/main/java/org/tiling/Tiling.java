package org.tiling;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineSegment;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBReader;
import com.vividsolutions.jts.io.WKTReader;

class SortByArea implements Comparator<Geometry> {

	/* Sort in descending order of geometry area */
	@Override
	public int compare(Geometry a, Geometry b) {
		return (int) (b.getArea() - a.getArea());
	}
	
}

class Cell {
	public Geometry getGeom() {
		return wkt;
	}
	
	public String getWKB() {
		return wkb.toString();
	}
	
	public String getWKT() {
		return wkt.toString();
	}

	public String getShapeId() {
		return shapeId;
	}

	public String getComboId() {
		return comboId;
	}

	private String shapeId;
	private String comboId;
	private Geometry wkb;
	private Geometry wkt;
	static WKBReader readerWKB = new WKBReader();
	static WKTReader readerWKT = new WKTReader();
	
	public Cell(String shapeId, String cellWKB, String cellWKT) {
		super();
		this.shapeId = shapeId.trim();
		try {
			this.wkb = readerWKB.read(WKBReader.hexToBytes(cellWKB.trim()));
			this.wkt = readerWKT.read(cellWKT.trim());
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public Cell(String shapeId, String comboId, Geometry geom) {
		super();
		this.shapeId = shapeId.trim();
		this.comboId = comboId;
		this.wkt = geom;
	}
	
	@Override
	public String toString() {
//		return "Cell [shape id=" + shapeId + ", combo id=" + comboId + ", wkb=" + wkb.toString() + ", wkt=" + wkt + "]";
		return "Cell [shape id=" + shapeId + ", combo id=" + comboId + ", wkt=" + wkt + "]";
	}
}

class Tiling {
	
	/* Input: list of contiguous rectangles. 
	 * Output: list of all possible rectangle combinations covering the input area. 
	 * The combinations arise from the input rectangles and from bigger rectangles 
	 * derived by combining the input rectangles vertically or horizontally. */
	static List<List<Geometry>> combineTiles (List<Geometry> inputRectangles) {
		
    	List<List<Geometry>> allCombos = new ArrayList<List<Geometry>>();
    	
    	if (inputRectangles.size() == 0 || inputRectangles.size() == 1) {
    		allCombos.add(inputRectangles);
    	}
    	
    	// If the two rectangles share an edge then merge them
    	else if (inputRectangles.size() == 2 && !inputRectangles.get(0).equals(inputRectangles.get(1))) {
    		List<Geometry> newCombo = new ArrayList<Geometry>();
    		allCombos.add(inputRectangles);
    		Coordinate[] coords = inputRectangles.get(0).getCoordinates();
    		List<LineSegment> firstRectEdges = new ArrayList<LineSegment>();
    		for (int i = 0; i < coords.length - 1; i++) {
    			firstRectEdges.add(new LineSegment(coords[i], coords[i+1]));
    		}
    		coords = inputRectangles.get(1).getCoordinates();
    		LineSegment secondRectEdge;
    		for (int i = 0; i < coords.length - 1; i++) {
    			secondRectEdge = new LineSegment(coords[i], coords[i+1]);
       			if (!firstRectEdges.contains(secondRectEdge)) {
    				secondRectEdge.reverse();
    				if (!firstRectEdges.contains(secondRectEdge)) {
    					continue;
    				}
    			}
       			newCombo.add(inputRectangles.get(0).union(inputRectangles.get(1)).convexHull());
	    		allCombos.add(newCombo);
	    		break;
       		}
    	}
    	
    	// Algorithm for more than two rectangles:
    	// Remove the first rectangle A
    	// Find all combinations CC of the rest of the rectangles
    	// For each C of these combinations:
    	//   For each R of its rectangles:
    	//     Find the combinations newCC with the first rectangle A
    	//     For each newC of the new combinations:
    	//       Add to the result newC together with the rest of the previous combination's rectangles
    	// NO:    Add to the combinations the rest of the previous combination's rectangles
    	else if (inputRectangles.size() > 2) {
    		Geometry firstRectangle = inputRectangles.remove(0);
    		List<List<Geometry>> combos = combineTiles(inputRectangles);
    		int c = combos.size() - 1;
    		List<Geometry> tempCombo;
    		while (c >= 0) {		// for each combination
    			tempCombo = combos.remove(c);
    			int p = tempCombo.size() - 1;
    			while (p >= 0) {	// for each rectangle
    				List<Geometry> newCombo = new ArrayList<Geometry>();
    				Geometry tempRectangle = tempCombo.remove(p);
    				newCombo.add(firstRectangle);
    				newCombo.add(tempRectangle);
    				List<List<Geometry>> newCombos = combineTiles(newCombo);
    				// add all rectangles of the tempCombo not included in the newCombo
    				for (int i = 0; i < newCombos.size(); i++) {
    					newCombos.get(i).addAll(tempCombo);
    				}
    				tempCombo.add(tempRectangle);
    				
    				for (List<Geometry> temp: newCombos) {
    					if (!containment(allCombos, temp)) {
    						allCombos.add(temp);
    					}
    				}
    				
    				p--;
    			}
    			c--;
    		}
    		
    		inputRectangles.add(firstRectangle);
    	}
    	
    	return allCombos;
    }
	
	
	/* Return true if List allCombos contains List newCombo, 
	 * otherwise return false. */ 
	static boolean containment(List<List<Geometry>> allCombos, List<Geometry> newCombo) {
		boolean containment = false;
		for (List<Geometry> combo: allCombos) {
			containment = false;
			if (combo.size() != newCombo.size())
				continue;
			for (Geometry geom: combo) {
				for (Geometry newGeom: newCombo) {
					containment = false;
					if (geom.equals(newGeom)) {
						containment = true;
						break;
					}
				}
				if (!containment) break;
			}
			if (containment) break;
		}
		return containment;
	}
	
//	/* Return true if geometries in combo are contiguous, 
//	 * otherwise return false. */ 
//	static boolean contiguity(List<Geometry> combo) {
//		boolean contiguity = true;
//		System.out.println("contiguity");
//		if (combo.size() == 1)
//			return contiguity;
//		
//		List<Geometry> geometries = new ArrayList<Geometry>();
//		geometries.addAll(combo);
//		
//		Geometry tempGeom;
//		
//		for (int i = 0; i < geometries.size(); i++) {
//			contiguity = false;
//			tempGeom = geometries.remove(i);
//			for (Geometry geom: geometries) {
//				System.out.println(tempGeom.intersection(geom).toString());
//				// UNCOMMENT NEXT LINE IF: Contiguity means there is a common linestring between two polygons
//				if (tempGeom.intersection(geom).toString().contains("LINESTRING")) {
//				// UNCOMMENT NEXT LINE IF: Contiguity means two polygons touch (a common point suffices)
//				//if (firstGeom.touches(geom)) {
//					contiguity = true;
//					break;
//				}
//			}
//			geometries.add(i, tempGeom);
//			if (!contiguity) break;
//		}
//		
//		return contiguity;
//	}
	
	
	/* Return true if geometries in combo are contiguous, 
	 * otherwise return false. */ 
	static boolean contiguity(List<Geometry> contTiles) {
		if (contTiles.size() == 1)
			return true;
		
		List<Geometry> restTiles = new ArrayList<Geometry>(contTiles);
		contTiles.clear();
		contTiles.add(restTiles.remove(0));
		
		for (int i = 0; i < contTiles.size(); i++) {
			for (Geometry geom: restTiles) {
				if (contTiles.get(i).intersection(geom).toString().contains("LINESTRING")) {
					contTiles.add(geom);
				}
			}
			restTiles.removeAll(contTiles);
		}
		
		if (restTiles.size() > 0)
			return false;
		
		return true;
	}
	
	/* Return true if maxArea is the area covered by the bigger geometry in combo,
	 * otherwise return false. */ 
	static boolean maxArea(List<Geometry> combo, int maxArea) {
		int area = 0;
		for (Geometry geom: combo) 
			area += geom.getArea();
		if (maxArea == area)
			return true;
		return false;
	}
	
	/* List<Geometry> combo --> Input list
	 * List data --> Temporary array to store current combination 
	 * start & end --> Staring and Ending indexes in combo
	 * index --> Current index in data 
	 * maxSize ---> Size of a combination to be returned 
	 * allCombos --> Combinations to be returned 
	 * area --> Necessary area to be covered by a rectangle in the combination in order to be accepted */
	static void combinationUtil(List<Geometry> combo, Geometry data[], int start, int end, int index, int maxSize, 
			List<List<Geometry>> allCombos, int area) {
		
		// Current combination is ready to be returned. 
		if (index == maxSize) {
			List<Geometry> newCombo = new ArrayList<Geometry>();
			for (int i = 0; i < maxSize; i++)
				newCombo.add(data[i]);
			
			if (!containment(allCombos, newCombo) && contiguity(newCombo) && maxArea(newCombo, area))
				allCombos.add(newCombo);
			return;
		}
		
		// Replace index with all possible elements. The condition 
		// "end-i+1 >= maxSize-index" makes sure that including one element 
		// at index will make a combination with remaining elements 
		// at remaining positions. 
		for (int i = start; i <= end && end-i+1 >= maxSize-index; i++) {
			data[index] = combo.get(i);
			combinationUtil(combo, data, i+1, end, index+1, maxSize, allCombos, area);
		}
	}
	
	/* Find all combinations of size 'desiredSize' 
	 * containing a rectangle covering area 'area' 
	 * from List<Geometry> combo of size 'size' */
	static List<List<Geometry>> getCombinations(List<Geometry> combo, int size, int desiredSize, int area) {
		List<List<Geometry>> allCombos = new ArrayList<List<Geometry>>();
		
		// A temporary array to store all combinations one by one 
		Geometry data[] = new Geometry[desiredSize]; 

		// Get all combinations using temporary array 'data[]'
		combinationUtil(combo, data, 0, size-1, 0, desiredSize, allCombos, area); 
		
		return allCombos;
	}
	
	// TO FIX: return origTiles (or part of them) if no combinations can be made!
	/* Input: list of contiguous rectangles. 
	 * Output: rectangle combinations that consist of the minimal possible 
	 * rectangles with the maximal size, derived from the input rectangles. */
	static List<List<Geometry>> generateRectangles (List<Geometry> origTiles, int desiredNumOfTiles, String shapeId) {
		List<List<Geometry>> largestTileCombos = new ArrayList<List<Geometry>>();
		List<List<Geometry>> newRoundCombos = new ArrayList<List<Geometry>>();
		List<List<Geometry>> allCombos = new ArrayList<List<Geometry>>();
				
//		int desiredNumOfTiles = 2;
		int prevMaxNumOfTiles, curNumOfTiles, minNumOfTiles, maxNumOfTiles;
		curNumOfTiles = minNumOfTiles = maxNumOfTiles = origTiles.size();	
		
		newRoundCombos.add(origTiles);
		
		// Create more combinations until the input is the same as the output,
		// no more combinations are added to the list.
		do {
			
			// Compute all combinations derived from uniting newRoundCombos
			for (List<Geometry> combo : newRoundCombos)
				allCombos.addAll(combineTiles(combo));
			
			// FIXED??? 
			if (allCombos.size() == 1) {
				largestTileCombos = allCombos;
				break;
			}
			
			prevMaxNumOfTiles = maxNumOfTiles;
			maxNumOfTiles = 0;
			newRoundCombos.clear();
			largestTileCombos.clear();
			
			// Keep all combinations with fewer rectangles than in the previous loop 
			// in List newRoundCombos.
			// Keep all combinations with the fewest rectangles in List largestTiles.
			for (List<Geometry> combo: allCombos) {
				curNumOfTiles = combo.size();
				if (prevMaxNumOfTiles <= curNumOfTiles) {
	    			continue;
	    		}
				
				if (!containment(newRoundCombos, combo))
					newRoundCombos.add(combo);
				
				if (maxNumOfTiles < curNumOfTiles) {
					maxNumOfTiles = curNumOfTiles;
				}
				
				if (minNumOfTiles > curNumOfTiles || largestTileCombos.isEmpty()) {
	    			minNumOfTiles = curNumOfTiles;
	    			largestTileCombos.clear();
	    			largestTileCombos.add(combo);
	    		}
				else if (minNumOfTiles == curNumOfTiles && !containment(largestTileCombos, combo)) {
	    			largestTileCombos.add(combo);
	    		}
	    	}
			
			if (minNumOfTiles == maxNumOfTiles || newRoundCombos.size() == allCombos.size())
				break;
			
			allCombos.clear();
			
		} while (prevMaxNumOfTiles > maxNumOfTiles);
					
		// Print all the largest tiles of each combination
		// Or find and print the desiredNumOfTiles largest ones 
		if (desiredNumOfTiles >= minNumOfTiles || desiredNumOfTiles == -1) { 
			System.out.println("\nShape " + shapeId + " has " + largestTileCombos.size() + " combinations with " + minNumOfTiles + " tiles:");
	    	for (List<Geometry> combo: largestTileCombos) {
	    		System.out.println(combo.toString());
	    	}
	    	
	    	return largestTileCombos;
		}
		else {
			System.out.println("\nShape " + shapeId + " has " + largestTileCombos.size() + " combinations with " + minNumOfTiles + " tiles:");
			for (List<Geometry> combo: largestTileCombos) {
	    		System.out.println(combo.toString());
	    	}
			
			int curArea, maxArea = 0;
			for (List<Geometry> combo: largestTileCombos) {
				Collections.sort(combo, new SortByArea());
				curArea = 0;
				for (int i = 0; i < desiredNumOfTiles; i++) 
					curArea += combo.get(i).getArea();
									
				if (maxArea < curArea) {
					maxArea = curArea;
					allCombos.clear();
					allCombos.addAll(getCombinations(combo, combo.size(), desiredNumOfTiles, maxArea)); 
	    		}
				else if (maxArea == curArea) {
					newRoundCombos = getCombinations(combo, combo.size(), desiredNumOfTiles, maxArea);
					for (List<Geometry> newCombo: newRoundCombos)
						if (!containment(allCombos, newCombo))
							allCombos.add(newCombo);
	    		}
	    	}
			
			System.out.println("\nShape " + shapeId + " has " + allCombos.size() + " combinations with " + desiredNumOfTiles + " tiles (maximal coverage of " + maxArea + " cells) :");
			for (List<Geometry> combo: allCombos)
	    		System.out.println(combo.toString());
			
			return allCombos;
		}
		
	}
	
	public static void main(String[] args) {
		// Read all original cells from different shapes from csv file
		List<Cell> cells = new ArrayList<Cell>();
		
		String inputFile = args[0];
		String outputFile = args[1];
		System.out.println("Input file: " + inputFile);
		System.out.println("Output file: " + outputFile);

		CSVParser csvParser = new CSVParserBuilder().withSeparator('|').build(); // custom separator
		try(CSVReader reader = new CSVReaderBuilder(
				new FileReader(inputFile))
				.withCSVParser(csvParser)   // custom CSV parser
				.withSkipLines(2)           // skip the first two lines, header info
				.build()){
			
			String[] lineInArray;
			while ((lineInArray = reader.readNext()) != null) {
				if (Arrays.toString(lineInArray).contains("rows"))
					break;
				
				cells.add(new Cell(lineInArray[0], lineInArray[1], lineInArray[2]));
			}
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (CsvValidationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		// PRINT FOR TESTING
//		for (Cell cell : cells)
//			System.out.println(cell.toString());
		
		// Call generateRectangles for each shape separately
		List<Geometry> origTiles = new ArrayList<Geometry>();
		List<List<Geometry>> allCombos;
		List<Cell> newCells = new ArrayList<Cell>();
		String shapeId = "";
		Integer comboId = 0;
		
		for (Cell cell : cells) {
			if (shapeId.isEmpty() || shapeId.equals(cell.getShapeId())) {
				shapeId = cell.getShapeId();
				origTiles.add(cell.getGeom());
				continue;
			}
						
			System.out.println("\nShape " + shapeId + " with " + origTiles.size() + " tiles:\n" + origTiles.toString());
			if (contiguity(origTiles)) {
//				System.out.println("Contiguous original tiles");
				allCombos = generateRectangles(origTiles, -1, shapeId);
				
				for (List<Geometry> combo : allCombos) {
					for (Geometry geom : combo) {
						newCells.add(new Cell(shapeId, comboId.toString(), geom));
					}
					comboId++;
				}
				comboId = 0;
			}
			else {
				System.out.println("\nShape " + shapeId + " with " + origTiles.size() + " tiles, is not contiguous");
			}
			
			origTiles.clear();
			origTiles.add(cell.getGeom());
			shapeId = cell.getShapeId();
		}
		
		System.out.println("\nShape " + shapeId + " with " + origTiles.size() + " tiles:\n" + origTiles.toString());
		if (contiguity(origTiles)) {
//			System.out.println("Contiguous original tiles");
			allCombos = generateRectangles(origTiles, -1, shapeId);
			for (List<Geometry> combo : allCombos) {
				for (Geometry geom : combo) {
					newCells.add(new Cell(shapeId, comboId.toString(), geom));
				}
				comboId++;
			}
			comboId = 0;
		}
		else {
			System.out.println("\nShape " + shapeId + " with " + origTiles.size() + " tiles, is not contiguous");
		}
		
		// PRINT FOR TESTING
//		System.out.println("\n");
//		for (Cell cell : newCells)
//			System.out.println(cell.toString());
		
		// Write the generated rectangles for each shape to a csv file 
		List<String[]> csvData = createCsvDataSimple(newCells);

        // default all fields are enclosed in double quotes
        // default separator is a comma
        try (CSVWriter writer = new CSVWriter(new FileWriter(outputFile))) {
            writer.writeAll(csvData);
        } catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}


	private static List<String[]> createCsvDataSimple(List<Cell> cells) {
		List<String[]> list = new ArrayList<>();
		list.add(new String[] {"shapeId", "comboId", "st_astext"});
		for (Cell cell : cells) {
//			Geometry geometry = ST_GeometryFromText(cell.getWKT(), 4326);
			String[] str = {cell.getShapeId(), cell.getComboId(), cell.getWKT()};
			list.add(str);
		}
		
        return list;
    }
	
	// Testing with some default geometries
//	public static void main(String[] args) {
//		List<Geometry> origTiles = new ArrayList<Geometry>();
//		List<List<Geometry>> largestTileCombos = new ArrayList<List<Geometry>>();
//		List<List<Geometry>> newRoundCombos = new ArrayList<List<Geometry>>();
//		List<List<Geometry>> allCombos = new ArrayList<List<Geometry>>();
//				
//		WKTReader reader = new WKTReader();
//		try {
//			// INPUT:
//			// | |E|D|
//			// |A|B|C|
//			//------------
//			// OUTPUT:
//			// [[ABC, ED], [A, BCDE]]
////			origTiles.add(reader.read("POLYGON ((7 3, 7 4, 8 4, 8 3, 7 3))"));
//			origTiles.add(reader.read("POLYGON ((8 3, 8 4, 9 4, 9 3, 8 3))"));
//			origTiles.add(reader.read("POLYGON ((9 3, 9 4, 10 4, 10 3, 9 3))"));
//			origTiles.add(reader.read("POLYGON ((9 4, 9 5, 10 5, 10 4, 9 4))"));
//			origTiles.add(reader.read("POLYGON ((8 4, 8 5, 9 5, 9 4, 8 4))"));
//			
//			// INPUT:
//			// | |E|D|
//			// |A|B|C|
//			//------------
//			// OUTPUT:
//			// [[ABC, ED], [A, BCDE]]
////			origTiles.add(reader.read("POLYGON ((7 3, 7 4, 8 4, 8 3, 7 3))"));
////			origTiles.add(reader.read("POLYGON ((8 3, 8 4, 9 4, 9 3, 8 3))"));
////			origTiles.add(reader.read("POLYGON ((9 3, 9 4, 10 4, 10 3, 9 3))"));
////			origTiles.add(reader.read("POLYGON ((9 4, 9 5, 10 5, 10 4, 9 4))"));
////			origTiles.add(reader.read("POLYGON ((8 4, 8 5, 9 5, 9 4, 8 4))"));
//			
//			// INPUT:
//			// |A| | |
//			// |C|D|E|
//			// |G|H| |
//			//------------
//			// OUTPUT:
//			// [[A, CDE, GH], [A, CDGH, E], [ACG, DH, E], [ACG, DE, H], [AC, DE, GH]]
//			//------------
//			// INPUT:
//			// |A| |B|
//			// |C|D|E|
//			// |G|H| |
//			//------------
//			// OUTPUT:
//			// [[A, CDGH, BE], [ACG, DH, BE]]
//			//------------
//			// INPUT:
//			// |A| | | |
//			// |C|D|E|F|
//			// |G|H| | |
//			//------------
//			// OUTPUT:
//			// [[A, CDGH, EF], [A, CDEF, GH], [AC, DEF, GH], [ACG, DH, EF], [ACG, DEF, H]]
//			//------------
//			//------------
//			// INPUT:
//			// |A| |B| |
//			// |C|D|E|F|
//			// |G|H| | |
//			//------------
//			//GEOMETRYCOLLECTION(POLYGON ((6 4, 6 5, 7 5, 7 4, 6 4)), POLYGON ((8 4, 8 5, 9 5, 9 4, 8 4)), POLYGON ((6 3, 6 4, 7 4, 7 3, 6 3)), POLYGON ((7 3, 7 4, 8 4, 8 3, 7 3)), POLYGON ((8 3, 8 4, 9 4, 9 3, 8 3)), POLYGON ((9 3, 9 4, 10 4, 10 3, 9 3)), POLYGON ((6 2, 6 3, 7 3, 7 2, 6 2)), POLYGON ((7 2, 7 3, 8 3, 8 2, 7 2)))
//			//------------
//			// [[A, CDGH, B, EF], [A, CDGH, BE, F], [A, B, CDEF, GH], [AC, G, B, DEF], 
//			//  [ACG, DH, BE, F], [ACG, DH, B, EF], [ACG, B, DEF, H]]
////			origTiles.add(reader.read("POLYGON ((6 4, 6 5, 7 5, 7 4, 6 4))"));
////			origTiles.add(reader.read("POLYGON ((8 4, 8 5, 9 5, 9 4, 8 4))"));
////			origTiles.add(reader.read("POLYGON ((6 3, 6 4, 7 4, 7 3, 6 3))"));
////			origTiles.add(reader.read("POLYGON ((7 3, 7 4, 8 4, 8 3, 7 3))"));
////			origTiles.add(reader.read("POLYGON ((8 3, 8 4, 9 4, 9 3, 8 3))"));
////			origTiles.add(reader.read("POLYGON ((9 3, 9 4, 10 4, 10 3, 9 3))"));
////			origTiles.add(reader.read("POLYGON ((6 2, 6 3, 7 3, 7 2, 6 2))"));
////			origTiles.add(reader.read("POLYGON ((7 2, 7 3, 8 3, 8 2, 7 2))"));
//			
//			// INPUT:
//			// |A|B|E|F|
//			// |C|D| |G|
//			//------------
//			//GEOMETRYCOLLECTION(POLYGON ((6 3, 6 4, 7 4, 7 3, 6 3)), POLYGON ((7 3, 7 4, 8 4, 8 3, 7 3)), POLYGON ((6 2, 6 3, 7 3, 7 2, 6 2)), POLYGON ((7 2, 7 3, 8 3, 8 2, 7 2)), POLYGON ((8 3, 8 4, 9 4, 9 3, 8 3)), POLYGON ((9 3, 9 4, 10 4, 10 3, 9 3)), POLYGON ((9 2, 9 3, 10 3, 10 2, 9 2)))
//			//------------
//			// [[ABE, CD, FG], [ABCD, E, FG], [ABCD, EF, G], [ABEF, CD, G]]
////			origTiles.add(reader.read("POLYGON ((6 3, 6 4, 7 4, 7 3, 6 3))"));
////			origTiles.add(reader.read("POLYGON ((7 3, 7 4, 8 4, 8 3, 7 3))"));
////			origTiles.add(reader.read("POLYGON ((6 2, 6 3, 7 3, 7 2, 6 2))"));
////			origTiles.add(reader.read("POLYGON ((7 2, 7 3, 8 3, 8 2, 7 2))"));
////			origTiles.add(reader.read("POLYGON ((8 3, 8 4, 9 4, 9 3, 8 3))"));
////			origTiles.add(reader.read("POLYGON ((9 3, 9 4, 10 4, 10 3, 9 3))"));
////			origTiles.add(reader.read("POLYGON ((9 2, 9 3, 10 3, 10 2, 9 2))"));
//			
//			// INPUT:
//			// |A| | |
//			// |B|C| |
//			// | |D|E|
//			// | |F|G|
//			//------------
//			//GEOMETRYCOLLECTION(POLYGON ((5 5, 5 6, 6 6, 6 5, 5 5)), POLYGON ((5 4, 5 5, 6 5, 6 4, 5 4)), POLYGON ((6 4, 6 5, 7 5, 7 4, 6 4)), POLYGON ((6 3, 6 4, 7 4, 7 3, 6 3)), POLYGON ((7 3, 7 4, 8 4, 8 3, 7 3)), POLYGON ((6 2, 6 3, 7 3, 7 2, 6 2)), POLYGON ((7 2, 7 3, 8 3, 8 2, 7 2)))
//			//------------
//			// [[A, BC, DEFG], [AB, C, DEFG], [AB, CDF, EG]]
////			origTiles.add(reader.read("POLYGON ((5 5, 5 6, 6 6, 6 5, 5 5))"));
////			origTiles.add(reader.read("POLYGON ((5 4, 5 5, 6 5, 6 4, 5 4))"));
////			origTiles.add(reader.read("POLYGON ((6 4, 6 5, 7 5, 7 4, 6 4))"));
////			origTiles.add(reader.read("POLYGON ((6 3, 6 4, 7 4, 7 3, 6 3))"));
////			origTiles.add(reader.read("POLYGON ((7 3, 7 4, 8 4, 8 3, 7 3))"));
////			origTiles.add(reader.read("POLYGON ((6 2, 6 3, 7 3, 7 2, 6 2))"));
////			origTiles.add(reader.read("POLYGON ((7 2, 7 3, 8 3, 8 2, 7 2))"));
//			
//			
//			int desiredNumOfTiles = 2;
//			int prevMaxNumOfTiles, curNumOfTiles, minNumOfTiles, maxNumOfTiles;
//			curNumOfTiles = minNumOfTiles = maxNumOfTiles = origTiles.size();	
//			
//			newRoundCombos.add(origTiles);
//			
//			// Create more combinations until the input is the same as the output,
//			// no more combinations are added to the list.
//			do {
//				
//				// Compute all combinations derived from uniting newRoundCombos
//				for (List<Geometry> combo : newRoundCombos)
//					allCombos.addAll(combineTiles(combo));
//				
//				prevMaxNumOfTiles = maxNumOfTiles;
//				maxNumOfTiles = 0;
//				newRoundCombos.clear();
//				largestTileCombos.clear();
//				
//				// Keep all combinations with fewer rectangles than in the previous loop 
//				// in List newRoundCombos.
//				// Keep all combinations with the fewest rectangles in List largestTiles.
//				for (List<Geometry> combo: allCombos) {
//					curNumOfTiles = combo.size();
//					if (prevMaxNumOfTiles <= curNumOfTiles) {
//		    			continue;
//		    		}
//					
//					if (!containment(newRoundCombos, combo))
//						newRoundCombos.add(combo);
//					
//					if (maxNumOfTiles < curNumOfTiles) {
//						maxNumOfTiles = curNumOfTiles;
//					}
//					
//					if (minNumOfTiles > curNumOfTiles || largestTileCombos.isEmpty()) {
//		    			minNumOfTiles = curNumOfTiles;
//		    			largestTileCombos.clear();
//		    			largestTileCombos.add(combo);
//		    		}
//					else if (minNumOfTiles == curNumOfTiles && !containment(largestTileCombos, combo)) {
//		    			largestTileCombos.add(combo);
//		    		}
//		    	}
//				
//				System.out.println("in a loop");
//				for (List<Geometry> combo: newRoundCombos) {
//		    		System.out.println(combo.toString());
//		    	}
//				
//				if (minNumOfTiles == maxNumOfTiles || newRoundCombos.size() == allCombos.size())
//					break;
//				
//				allCombos.clear();
//				
//			} while (prevMaxNumOfTiles > maxNumOfTiles);
//						
//			// Print all the largest tiles of each combination
//			// Or find and print the desiredNumOfTiles largest ones 
//			if (desiredNumOfTiles >= minNumOfTiles) { 
//				System.out.println(largestTileCombos.size() + " combinations with " + minNumOfTiles + " tiles:");
//		    	for (List<Geometry> combo: largestTileCombos) {
//		    		System.out.println(combo.toString());
//		    	}
//			}
//			else {
//				System.out.println(largestTileCombos.size() + " combinations with " + minNumOfTiles + " tiles:");
//				for (List<Geometry> combo: largestTileCombos) {
//		    		System.out.println(combo.toString());
//		    	}
//				
//				int curArea, maxArea = 0;
//				for (List<Geometry> combo: largestTileCombos) {
//					Collections.sort(combo, new SortByArea());
//					curArea = 0;
//					for (int i = 0; i < desiredNumOfTiles; i++) 
//						curArea += combo.get(i).getArea();
//										
//					if (maxArea < curArea) {
//						maxArea = curArea;
//						allCombos.clear();
//						allCombos.addAll(getCombinations(combo, combo.size(), desiredNumOfTiles, maxArea)); 
//		    		}
//					else if (maxArea == curArea) {
//						newRoundCombos = getCombinations(combo, combo.size(), desiredNumOfTiles, maxArea);
//						for (List<Geometry> newCombo: newRoundCombos)
//							if (!containment(allCombos, newCombo))
//								allCombos.add(newCombo);
//		    		}
//		    	}
//				
//				System.out.println(allCombos.size() + " combinations with " + desiredNumOfTiles + " tiles (maximal coverage of " + maxArea + " cells) :");
//				for (List<Geometry> combo: allCombos)
//		    		System.out.println(combo.toString());
//				
//			}
//					
//		} catch (ParseException e) {
//			e.printStackTrace();
//		}
//	}
	
} 
