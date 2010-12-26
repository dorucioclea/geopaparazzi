/*
 * Geopaparazzi - Digital field mapping on Android based devices
 * Copyright (C) 2010  HydroloGIS (www.hydrologis.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package eu.hydrologis.geopaparazzi.gpx;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import eu.hydrologis.geopaparazzi.util.PointF3D;
import eu.hydrologis.geopaparazzi.util.debug.Logger;

/**
 * Gpx Track parser
 * 
 * @author Andrea Antonello (www.hydrologis.com)
 */
public class GpxTrackParser extends DefaultHandler implements IGpxParser {
    private float minLat = Float.POSITIVE_INFINITY;
    private float maxLat = Float.NEGATIVE_INFINITY;
    private float minLon = Float.POSITIVE_INFINITY;
    private float maxLon = Float.NEGATIVE_INFINITY;
    private float minElev = Float.POSITIVE_INFINITY;
    private float maxElev = Float.NEGATIVE_INFINITY;
    private float length = 0f;

    private ArrayList<PointF3D> pointsList = new ArrayList<PointF3D>(50);
    private boolean isElev = false;
    private int currentIndex = 0;

    public GpxTrackParser() {
        clear();
    }

    public void clear() {
        pointsList.clear();
    }

    public void read( String filename ) {
        clear();
        try {
            FileInputStream in = new FileInputStream(new File(filename));
            InputSource source = new InputSource(in);

            SAXParserFactory saxFactory = SAXParserFactory.newInstance();
            SAXParser parser = saxFactory.newSAXParser();
            XMLReader reader = parser.getXMLReader();

            reader.setContentHandler(this);
            reader.parse(source);
            in.close();
        } catch (Exception e) {
            Logger.e(this, e.getLocalizedMessage(), e);
            e.printStackTrace();
        }
    }

    /*
     *  DefaultHandler::startElement() fires whenever an XML start tag is encountered 
     */
    public void startElement( String uri, String localName, String qName, Attributes attributes ) throws SAXException {
        // the <trkpt> element has attributes which specify latitude and longitude (it has child
        // elements that specify the time and elevation)
        if (localName.compareToIgnoreCase("trkpt") == 0) { //$NON-NLS-1$
            float lon = (float) Double.parseDouble(attributes.getValue("lon")); //$NON-NLS-1$
            float lat = (float) Double.parseDouble(attributes.getValue("lat")); //$NON-NLS-1$
            if (lat < minLat) {
                minLat = lat;
            }
            if (lat > maxLat) {
                maxLat = lat;
            }
            if (lon < minLon) {
                minLon = lon;
            }
            if (lon > maxLon) {
                maxLon = lon;
            }
            currentIndex = pointsList.size();
            PointF3D pointF3D = new PointF3D(lon, lat);
            pointsList.add(pointF3D);
            if (currentIndex > 0) {
                length = length + pointF3D.distance(pointsList.get(currentIndex - 1));
            }
        }
        if (localName.startsWith("ele")) { //$NON-NLS-1$
            isElev = true;
        }
    }

    public void characters( char[] ch, int start, int length ) throws SAXException {
        if (isElev) {
            String elevStr = new String(ch, start, length);
            float elev = Float.parseFloat(elevStr.trim());
            PointF3D pointF3D = pointsList.get(currentIndex);
            if (pointF3D.isHasZ()) {
                throw new RuntimeException();
            }
            if (elev < minElev) {
                minElev = elev;
            }
            if (elev > maxElev) {
                maxElev = elev;
            }
            pointF3D.setZ(elev);
            isElev = false;
        }
    }

    public ArrayList<PointF3D> getPoints() {
        return pointsList;
    }

    public List<String> getNames() {
        return null;
    }

    public float getEastBound() {
        return maxLon;
    }

    public float getNorthBound() {
        return maxLat;
    }

    public float getSouthBound() {
        return minLat;
    }

    public float getWestBound() {
        return minLon;
    }

    public float getMaxElev() {
        return maxElev;
    }

    public float getMinElev() {
        return minElev;
    }

    public float getLength() {
        return length;
    }
}
