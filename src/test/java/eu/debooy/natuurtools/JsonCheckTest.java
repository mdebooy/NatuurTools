/*
 * Copyright (c) 2021 Marco de Booij
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the Licence. You may
 * obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
package eu.debooy.natuurtools;

import eu.debooy.doosutils.test.BatchTest;
import static org.junit.Assert.assertEquals;
import org.junit.Test;


/**
 * @author booymar
 */
public class JsonCheckTest extends BatchTest {
  protected void execute(String[] args) {
    before();
    JsonCheck.execute(args);
    after();
  }

  @Test
  public void testLeeg() {
    var args  = new String[] {};

    execute(args);

    assertEquals("Zonder parameters - fouten", 1, err.size());
  }
}
