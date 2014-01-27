package ucar.nc2.grib.collection;

import ucar.nc2.time.CalendarDateRange;
import ucar.nc2.time.CalendarDateUnit;
import ucar.sparr.Coordinate;
import ucar.sparr.CoordinateBuilderImpl;
import ucar.nc2.grib.TimeCoord;
import ucar.nc2.grib.grib2.Grib2Pds;
import ucar.nc2.grib.grib2.Grib2Record;
import ucar.nc2.grib.grib2.Grib2Utils;
import ucar.nc2.grib.grib2.table.Grib2Customizer;
import ucar.nc2.time.CalendarDate;
import ucar.nc2.time.CalendarPeriod;
import ucar.nc2.util.Indent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;

/**
 * Time coordinates that are intervals.
 *
 * @author John
 * @since 11/28/13
 */
public class CoordinateTimeIntv extends CoordinateTimeAbstract implements Coordinate {
  private final List<TimeCoord.Tinv> timeIntervals;

  //public CoordinateTimeIntv(Grib2Customizer cust, CalendarPeriod timeUnit, int code, List<TimeCoord.Tinv> timeIntervals) {
  public CoordinateTimeIntv(int code, CalendarPeriod timeUnit, List<TimeCoord.Tinv> timeIntervals) {
    super(code, timeUnit);
    this.timeIntervals = Collections.unmodifiableList(timeIntervals);
  }

  CoordinateTimeIntv(CoordinateTimeIntv org, int offset) {
    super(org.getCode(), org.getTimeUnit());
    List<TimeCoord.Tinv> vals = new ArrayList<>(org.getSize());
    for (TimeCoord.Tinv orgVal : org.getTimeIntervals()) vals.add(new TimeCoord.Tinv(orgVal.getBounds1()+offset, orgVal.getBounds2()+offset));
    this.timeIntervals = Collections.unmodifiableList(vals);
  }

  public List<TimeCoord.Tinv> getTimeIntervals() {
    return timeIntervals;
  }

  @Override
  public List<? extends Object> getValues() {
    return timeIntervals;
  }

  @Override
  public Object getValue(int idx) {
    if (idx >= timeIntervals.size())
      System.out.println("HEY");
    return timeIntervals.get(idx);
  }

  @Override
  public int getIndex(Object val) {
    return timeIntervals.indexOf(val);
  }

  public int getSize() {
    return timeIntervals.size();
  }
  @Override
  public Type getType() {
    return Type.timeIntv;
  }

  /* public void setRefDate(CalendarDate refDate) {
    this.refDate = refDate;
  } */

  public String getTimeIntervalName() {

    // are they the same length ?
    int firstValue = -1;
    boolean same = true;
    for (TimeCoord.Tinv tinv : timeIntervals) {
      int value = (tinv.getBounds2() - tinv.getBounds1());
      if (firstValue < 0) firstValue = value;
      else if (value != firstValue) same = false;
    }

    if (same) {
      firstValue = (int) (firstValue * getTimeUnitScale());
      return firstValue + "_" + timeUnit.getField().toString();
    } else {
      return "Mixed_intervals";
    }
  }

  public List<CalendarDate> makeCalendarDates(ucar.nc2.time.Calendar cal, CalendarDate refDate) {
    CalendarDateUnit cdu = CalendarDateUnit.withCalendar(cal, periodName+" since "+ refDate.toString());
    List<CalendarDate> result = new ArrayList<>(getSize());
    for (TimeCoord.Tinv val : getTimeIntervals())
      result.add(cdu.makeCalendarDate(val.getBounds2())); // use the upper bound - same as iosp uses for coord
    return result;
  }

  public CalendarDateRange makeCalendarDateRange(ucar.nc2.time.Calendar cal, CalendarDate refDate) {
    CalendarDateUnit cdu = CalendarDateUnit.withCalendar(cal, periodName + " since " + refDate.toString());
    CalendarDate start = cdu.makeCalendarDate(timeIntervals.get(0).getBounds2());
    CalendarDate end = cdu.makeCalendarDate(timeIntervals.get(getSize()-1).getBounds2());
    return CalendarDateRange.of(start, end);
  }

  @Override
  public void showInfo(Formatter info, Indent indent) {
    info.format("%s%s:", indent, getType());
     for (TimeCoord.Tinv cd : timeIntervals)
       info.format(" %s,", cd);
    info.format(" (%d) %n", timeIntervals.size());
  }

  @Override
  public void showCoords(Formatter info) {
    info.format("Time Interval offsets: (%s) %n", getUnit());
    for (TimeCoord.Tinv cd : timeIntervals)
      info.format("   (%3d - %3d)  %d%n", cd.getBounds1(), cd.getBounds2(), cd.getBounds2() - cd.getBounds1());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    CoordinateTimeIntv that = (CoordinateTimeIntv) o;

    if (code != that.code) return false;
    if (!timeIntervals.equals(that.timeIntervals)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = code;
    result = 31 * result + timeIntervals.hashCode();
    return result;
  }

  ///////////////////////////////////////////////////////////

 /* @Override
  public CoordinateBuilder makeBuilder() {
    return new Builder(cust, timeUnit, code);
  }  */

  static public class Builder extends CoordinateBuilderImpl<Grib2Record> {
    private final Grib2Customizer cust;
    private final int code;                  // pdsFirst.getTimeUnit()
    private final CalendarPeriod timeUnit;

    public Builder(Grib2Customizer cust, CalendarPeriod timeUnit, int code) {
      this.cust = cust;
      this.timeUnit = timeUnit;
      this.code = code;
    }

    @Override
    public Object extract(Grib2Record gr) {
      CalendarDate refDate =  gr.getReferenceDate();

      CalendarPeriod timeUnitUse = timeUnit;
      Grib2Pds pds = gr.getPDS();
      int tu2 = pds.getTimeUnit();
      if (tu2 != code) {
        System.out.printf("Time unit diff %d != %d%n", tu2, code);
        int unit = cust.convertTimeUnit(tu2);
        timeUnitUse = Grib2Utils.getCalendarPeriod(unit);
      }

      TimeCoord.TinvDate tinvd = cust.getForecastTimeInterval(gr);
      TimeCoord.Tinv tinv = tinvd.convertReferenceDate(refDate, timeUnitUse);
      return tinv;
    }

    @Override
    public Coordinate makeCoordinate(List<Object> values) {
      List<TimeCoord.Tinv> offsetSorted = new ArrayList<>(values.size());
      for (Object val : values) offsetSorted.add( (TimeCoord.Tinv) val);
      Collections.sort(offsetSorted);

      return new CoordinateTimeIntv(code, timeUnit, offsetSorted);
    }
  }

}