# Introduction #
The DateFormat strings used by TT-RSS Reader are those of the [Android SDK](http://developer.android.com/reference/java/text/DateFormat.html). The Android SDK documentation is intended for developers, which makes it a bit hard to understand for some users. This article aims to give a simple overview of the more common components that you can use to compose a date / time format string.

# Components #
You can combine any of the following components in any order you like:
The examples for each component refer to this example date and time: Friday, 1 July 2011, 09:01:01 PM

  * a = AM/PM indicator (PM)
  * d = Day of month (1), dd for zero-padding (01)
  * E = Day of week (Fri), EEEE to get the day name (Friday)
  * h = Hour - 12-hour format (9), hh for zero-padding (09)
  * k = Hour - 24-hour format (21), kk for zero-padding (21)
  * m = Minute (1), mm for zero-padding (01)
  * M = Month (7), MM for zero-padding (07), MMMM to get the month name (July)
  * s = Second (1), ss for zero-padding (01)
  * y = Year (11), yyyy for 4-digit year (2011)

So, to get our example date format, we would enter 'EEEE, d MMMM yyyy, hh:mm:ss a'.