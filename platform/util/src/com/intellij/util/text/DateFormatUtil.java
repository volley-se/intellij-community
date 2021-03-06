/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.text;

import com.intellij.CommonBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DateFormatUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.util.text.DateFormatUtil");

  public static final long SECOND = 1000;
  public static final long MINUTE = SECOND * 60;
  public static final long HOUR = MINUTE * 60;
  public static final long DAY = HOUR * 24;
  public static final long WEEK = DAY * 7;
  public static final long MONTH = DAY * 30;
  public static final long YEAR = DAY * 365;
  public static final long DAY_FACTOR = 24L * 60 * 60 * 1000;

  private enum Period {YEAR, MONTH, WEEK, DAY, HOUR, MINUTE}
  private enum DateType {TIME, DATE, DATETIME}

  // do not expose this constants - they are very likely to be changed in future
  private static final SyncDateFormat DATE_FORMAT = getFormat(DateFormat.SHORT, DateType.DATE);
  private static final SyncDateFormat TIME_FORMAT = getFormat(DateFormat.SHORT, DateType.TIME);
  private static final SyncDateFormat TIME_WITH_SECONDS_FORMAT = getFormat(DateFormat.MEDIUM, DateType.TIME);
  private static final SyncDateFormat DATE_TIME_FORMAT = getFormat(DateFormat.SHORT, DateType.DATETIME);
  private static final SyncDateFormat ABOUT_DATE_FORMAT = new SyncDateFormat(DateFormat.getDateInstance(DateFormat.LONG, Locale.US));

  private static final long[] DENOMINATORS = {YEAR, MONTH, WEEK, DAY, HOUR, MINUTE};
  private static final Period[] PERIODS = {Period.YEAR, Period.MONTH, Period.WEEK, Period.DAY, Period.HOUR, Period.MINUTE};

  private DateFormatUtil() { }

  public static long getDifferenceInDays(final Date startDate, final Date endDate) {
    return (endDate.getTime() - startDate.getTime() + DAY_FACTOR - 1000) / DAY_FACTOR;
  }

  @NotNull
  public static SyncDateFormat getDateFormat() {
    return DATE_FORMAT;
  }

  @NotNull
  public static SyncDateFormat getTimeFormat() {
    return TIME_FORMAT;
  }

  @NotNull
  public static SyncDateFormat getTimeWithSecondsFormat() {
    return TIME_WITH_SECONDS_FORMAT;
  }

  @NotNull
  public static SyncDateFormat getDateTimeFormat() {
    return DATE_TIME_FORMAT;
  }

  @NotNull
  public static String formatTime(@NotNull Date time) {
    return formatTime(time.getTime());
  }

  @NotNull
  public static String formatTime(long time) {
    return getTimeFormat().format(time);
  }

  @NotNull
  public static String formatTimeWithSeconds(@NotNull Date time) {
    return formatTimeWithSeconds(time.getTime());
  }

  @NotNull
  public static String formatTimeWithSeconds(long time) {
    return getTimeWithSecondsFormat().format(time);
  }

  @NotNull
  public static String formatDate(@NotNull Date time) {
    return formatDate(time.getTime());
  }

  @NotNull
  public static String formatDate(long time) {
    return getDateFormat().format(time);
  }

  @NotNull
  public static String formatPrettyDate(@NotNull Date date) {
    return formatPrettyDate(date.getTime());
  }

  @NotNull
  public static String formatPrettyDate(long time) {
    return doFormatPretty(time, false);
  }

  @NotNull
  public static String formatDateTime(Date date) {
    return formatDateTime(date.getTime());
  }

  @NotNull
  public static String formatDateTime(long time) {
    return getDateTimeFormat().format(time);
  }

  @NotNull
  public static String formatPrettyDateTime(Date date) {
    return formatPrettyDateTime(date.getTime());
  }

  @NotNull
  public static String formatPrettyDateTime(long time) {
    return doFormatPretty(time, true);
  }

  @NotNull
  private static String doFormatPretty(long time, boolean formatTime) {
    long currentTime = Clock.getTime();

    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(currentTime);

    int currentYear = c.get(Calendar.YEAR);
    int currentDayOfYear = c.get(Calendar.DAY_OF_YEAR);

    c.setTimeInMillis(time);

    int year = c.get(Calendar.YEAR);
    int dayOfYear = c.get(Calendar.DAY_OF_YEAR);

    if (formatTime) {
      long delta = currentTime - time;
      if (delta <= HOUR && delta >= 0) {
        return CommonBundle.message("date.format.minutes.ago", (int)Math.rint(delta / (double)MINUTE));
      }
    }

    boolean isToday = currentYear == year && currentDayOfYear == dayOfYear;
    if (isToday) {
      String result = CommonBundle.message("date.format.today");
      if (formatTime) result += " " + TIME_FORMAT.format(time);
      return result;
    }

    boolean isYesterdayOnPreviousYear =
      (currentYear == year + 1) && currentDayOfYear == 1 && dayOfYear == c.getActualMaximum(Calendar.DAY_OF_YEAR);
    boolean isYesterday = isYesterdayOnPreviousYear || (currentYear == year && currentDayOfYear == dayOfYear + 1);

    if (isYesterday) {
      String result = CommonBundle.message("date.format.yesterday");
      if (formatTime) result += " " + TIME_FORMAT.format(time);
      return result;
    }

    return formatTime ? DATE_TIME_FORMAT.format(time) : DATE_FORMAT.format(time);
  }

  @NotNull
  public static String formatDuration(long delta) {
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < DENOMINATORS.length; i++) {
      long denominator = DENOMINATORS[i];
      int n = (int)(delta / denominator);
      if (n != 0) {
        buf.append(composeDurationMessage(PERIODS[i], n));
        buf.append(' ');
        delta = delta % denominator;
      }
    }

    if (buf.length() == 0) return CommonBundle.message("date.format.less.than.a.minute");
    return buf.toString().trim();
  }

  private static String composeDurationMessage(final Period period, final int n) {
    switch (period) {
      case DAY:
        return CommonBundle.message("date.format.n.days", n);
      case MINUTE:
        return CommonBundle.message("date.format.n.minutes", n);
      case HOUR:
        return CommonBundle.message("date.format.n.hours", n);
      case MONTH:
        return CommonBundle.message("date.format.n.months", n);
      case WEEK:
        return CommonBundle.message("date.format.n.weeks", n);
      default:
        return CommonBundle.message("date.format.n.years", n);
    }
  }

  @NotNull
  public static String formatFrequency(long time) {
    return CommonBundle.message("date.frequency", formatBetweenDates(time, 0));
  }

  @NotNull
  public static String formatBetweenDates(long d1, long d2) {
    long delta = Math.abs(d1 - d2);
    if (delta == 0) return CommonBundle.message("date.format.right.now");

    int n = -1;
    int i;
    for (i = 0; i < DENOMINATORS.length; i++) {
      long denominator = DENOMINATORS[i];
      if (delta >= denominator) {
        n = (int)(delta / denominator);
        break;
      }
    }

    if (d2 > d1) {
      if (n <= 0) {
        return CommonBundle.message("date.format.a.few.moments.ago");
      }
      else {
        return someTimeAgoMessage(PERIODS[i], n);
      }
    }
    else if (d2 < d1) {
      if (n <= 0) {
        return CommonBundle.message("date.format.in.a.few.moments");
      }
      else {
        return composeInSomeTimeMessage(PERIODS[i], n);
      }
    }

    return "";
  }

  @NotNull
  public static String formatAboutDialogDate(@NotNull Date date) {
    return ABOUT_DATE_FORMAT.format(date);
  }

  // helpers

  private static SyncDateFormat getFormat(int format, DateType type) {
    DateFormat result = null;

    if (SystemInfo.isMac) {
      try {
        result = getMacTimeFormat(format, type);
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    if (result == null) {
      switch (type) {
        case TIME:
          result = DateFormat.getTimeInstance(format);
          break;
        case DATE:
          result = DateFormat.getDateInstance(format);
          break;
        case DATETIME:
          result = DateFormat.getDateTimeInstance(format, format);
          break;
      }
    }

    return new SyncDateFormat(result);
  }

  private static String someTimeAgoMessage(final Period period, final int n) {
    switch (period) {
      case DAY:
        return CommonBundle.message("date.format.n.days.ago", n);
      case MINUTE:
        return CommonBundle.message("date.format.n.minutes.ago", n);
      case HOUR:
        return CommonBundle.message("date.format.n.hours.ago", n);
      case MONTH:
        return CommonBundle.message("date.format.n.months.ago", n);
      case WEEK:
        return CommonBundle.message("date.format.n.weeks.ago", n);
      default:
        return CommonBundle.message("date.format.n.years.ago", n);
    }
  }

  private static String composeInSomeTimeMessage(final Period period, final int n) {
    switch (period) {
      case DAY:
        return CommonBundle.message("date.format.in.n.days", n);
      case MINUTE:
        return CommonBundle.message("date.format.in.n.minutes", n);
      case HOUR:
        return CommonBundle.message("date.format.in.n.hours", n);
      case MONTH:
        return CommonBundle.message("date.format.in.n.months", n);
      case WEEK:
        return CommonBundle.message("date.format.in.n.weeks", n);
      default:
        return CommonBundle.message("date.format.in.n.years", n);
    }
  }

  private static final int MacFormatterNoStyle = 0;
  private static final int MacFormatterShortStyle = 1;
  private static final int MacFormatterMediumStyle = 2;
  private static final int MacFormatterLongStyle = 3;
  private static final int MacFormatterFullStyle = 4;
  private static final int MacFormatterBehavior_10_4 = 1040;

  @NotNull
  private static SimpleDateFormat getMacTimeFormat(final int type, @NotNull final DateType dateType) {
    final ID autoReleasePool = Foundation.invoke("NSAutoreleasePool", "new");
    try {
      final ID dateFormatter = Foundation.invoke("NSDateFormatter", "new");
      Foundation.invoke(dateFormatter, Foundation.createSelector("setFormatterBehavior:"), MacFormatterBehavior_10_4);

      int style;
      switch (type) {
        case DateFormat.SHORT:
          style = MacFormatterShortStyle;
          break;
        case DateFormat.MEDIUM:
          style = MacFormatterMediumStyle;
          break;
        case DateFormat.LONG:
          style = MacFormatterLongStyle;
          break;
        case DateFormat.FULL:
        default:
          style = MacFormatterFullStyle;
          break;
      }

      int timeStyle;
      int dateStyle;
      switch (dateType) {
        case DATE:
          timeStyle = MacFormatterNoStyle;
          dateStyle = style;
          break;
        case TIME:
          timeStyle = style;
          dateStyle = MacFormatterNoStyle;
          break;
        case DATETIME:
        default:
          timeStyle = style;
          dateStyle = style;
          break;
      }

      Foundation.invoke(dateFormatter, Foundation.createSelector("setTimeStyle:"), timeStyle);
      Foundation.invoke(dateFormatter, Foundation.createSelector("setDateStyle:"), dateStyle);
      String format = Foundation.toStringViaUTF8(Foundation.invoke(dateFormatter, Foundation.createSelector("dateFormat")));
      assert format != null;
      return new SimpleDateFormat(format.trim());
    }
    finally {
      Foundation.invoke(autoReleasePool, Foundation.createSelector("release"));
    }
  }
}