package org.dynmap.modsupport;

/**
 * Standard box block model: cube with limits on X, Y, and/or Z - standard cube texture indexes
 */
public interface BoxBlockModel extends BlockModel {
    /**
     * Set x range
     * @param xmin - x minimum
     * @param xmax - x maximum
     */
    void setXRange(double xmin, double xmax);
    /**
     * Set y range
     * @param ymin - y minimum
     * @param ymax - y maximum
     */
    void setYRange(double ymin, double ymax);
    /**
     * Set z range
     * @param zmin - z minimum
     * @param zmax - z maximum
     */
    void setZRange(double zmin, double zmax);
}
