/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests that the canvas never schedules a repaint from inside its own paint pass.
 *
 * <p>When two buses of different widths are connected, the canvas paints an error marker for
 * the offending point. Doing that used to go through {@code setArrows()}, which ended with a
 * {@code viewport.repaint()} - and since the paint pass itself calls {@code setArrows()}, every
 * paint scheduled another paint, forever. The result was a UI locked at 100% of one CPU core,
 * with the heap growing by roughly 20 MB per second (see issue #3121).
 */
@DisplayName("Canvas - no repaint scheduled from the paint pass")
class CanvasRepaintLoopTest {

  @Test
  @DisplayName("Painting a circuit with incompatible bus widths does not schedule another paint")
  void paintingWithIncompatibleWidthsDoesNotScheduleAnotherPaint() {
    final var project = projectWithIncompatibleWidths();
    final var canvas = new Canvas(project);
    final var graphics = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB).createGraphics();

    final var repaintsBefore = canvas.viewport.repaintRequests;
    canvas.viewport.paintContents(graphics);

    assertEquals(
        repaintsBefore,
        canvas.viewport.repaintRequests,
        "Painting must not request a repaint: the paint pass calls setArrows(), and a repaint"
                + " from there starts an endless repaint loop (issue #3121)");
  }

  @Test
  @DisplayName("setArrows does not request a repaint by itself")
  void setArrowsDoesNotRequestARepaint() {
    final var canvas = new Canvas(projectWithIncompatibleWidths());

    final var repaintsBefore = canvas.viewport.repaintRequests;
    canvas.setArrows(0, 0, 100, 100);

    assertEquals(
        repaintsBefore,
        canvas.viewport.repaintRequests,
        "setArrows() is called from the paint pass, so it must not request a repaint");
  }

  @Test
  @DisplayName("updateArrows still requests a repaint")
  void updateArrowsRequestsARepaint() {
    final var canvas = new Canvas(projectWithIncompatibleWidths());

    final var repaintsBefore = canvas.viewport.repaintRequests;
    canvas.updateArrows();

    assertTrue(
        canvas.viewport.repaintRequests > repaintsBefore,
        "updateArrows() changes the arrows outside of a paint pass, so it must refresh the"
                + " viewport");
  }

  /**
   * Builds a project whose main circuit connects an 8-bit pin to a 4-bit pin, which leaves the
   * bundle with incompatible widths.
   */
  private static Project projectWithIncompatibleWidths() {
    final var file = LogisimFile.createNew(new Loader(null), null);
    final var project = new Project(file);
    final var circuit = file.getMainCircuit();
    circuit.setProject(project);
    project.setCurrentCircuit(circuit);

    final var outputAttrs = Pin.FACTORY.createAttributeSet();
    outputAttrs.setValue(Pin.ATTR_TYPE, Pin.OUTPUT);
    outputAttrs.setValue(StdAttr.WIDTH, BitWidth.create(8));
    final var output = Pin.FACTORY.createComponent(Location.create(100, 100, true), outputAttrs);

    final var inputAttrs = Pin.FACTORY.createAttributeSet();
    inputAttrs.setValue(StdAttr.WIDTH, BitWidth.create(4));
    final var input = Pin.FACTORY.createComponent(Location.create(300, 100, true), inputAttrs);

    add(circuit, output);
    add(circuit, input);
    add(circuit, Wire.create(output.getLocation(), input.getLocation()));

    final var exceptions = circuit.getWidthIncompatibilityData();
    assertNotNull(exceptions, "The setup is expected to produce width incompatibility data");
    assertTrue(
        !exceptions.isEmpty(),
        "The setup is expected to produce width incompatibility data, but got none");

    return project;
  }

  private static void add(Circuit circuit, Component component) {
    final var mutation = new CircuitMutation(circuit);
    mutation.add(component);
    mutation.execute();
  }
}
