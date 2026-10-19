package com.lyle.workbench;

import static com.lyle.workbench.Model.*;

public interface Analyst {
  boolean available();

  String model();

  Draft analyze(String prompt);
}
