/*
 * Copyright 2004 - 2012 Mirko Nasato and contributors
 *           2016 - 2020 Simon Braconnier and contributors
 *
 * This file is part of JODConverter - Java OpenDocument Converter.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jodconverter.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeNoException;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;

import com.sun.star.lang.XComponent;
import org.apache.commons.io.FileUtils;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.jodconverter.AbstractOfficeITest;
import org.jodconverter.LocalConverter;
import org.jodconverter.office.OfficeContext;

public class DefaultFilterChainITest extends AbstractOfficeITest {

  private static final String SOURCE_FILENAME = "test_multi_page.doc";
  private static final File SOURCE_FILE = new File(DOCUMENTS_DIR, SOURCE_FILENAME);

  @ClassRule public static TemporaryFolder testFolder = new TemporaryFolder();

  /**
   * Test that reseting a chain will actually allow us to reuse it.
   *
   * @throws Exception if an error occurs.
   */
  @Test
  public void reset_WithPageCounterAndSelector_ShoudCountProperSizesForBothUsage()
      throws Exception {

    final File targetFile1 = new File(testFolder.getRoot(), SOURCE_FILENAME + ".page1.txt");
    final File targetFile2 = new File(testFolder.getRoot(), SOURCE_FILENAME + ".page1again.txt");

    final PageCounterFilter count1 = new PageCounterFilter();
    final PageCounterFilter count2 = new PageCounterFilter();

    // Test the filters
    final DefaultFilterChain chain =
        new DefaultFilterChain(
            count1, new PagesSelectorFilter(1), new RefreshFilter(false), count2);
    final LocalConverter converter = LocalConverter.builder().filterChain(chain).build();
    converter.convert(SOURCE_FILE).to(targetFile1).execute();

    final String content = FileUtils.readFileToString(targetFile1, StandardCharsets.UTF_8);
    assertThat(content)
        .contains("Test document Page 1")
        .doesNotContain("Test document Page 2")
        .doesNotContain("Test document Page 3");
    assertThat(count1.getPageCount()).isEqualTo(3);
    assertThat(count2.getPageCount()).isEqualTo(1);

    // Reset the chain and test the filters again
    chain.reset();
    converter.convert(targetFile1).to(targetFile2).execute();
    assertThat(count1.getPageCount()).isEqualTo(1);
    assertThat(count2.getPageCount()).isEqualTo(1);
  }

  /**
   * Test that putting off the automatic insertion of refresh filter won't execute any refresh.
   *
   * @throws Exception if an error occurs.
   */
  @Test
  public void reset_WithEndsWithRefreshFilterOff_ShoudNotApplyRefreshFilter() throws Exception {

    // Replace the LAST_REFRESH singleton
    final RefreshFilter refreshFilter = mock(RefreshFilter.class);
    try {
      // TODO: Find a way to test under jdk12/13
      // This is not supported with jdk 12/13
      // https://bugs.openjdk.java.net/browse/JDK-8217225
      setFinalStatic(RefreshFilter.class.getDeclaredField("LAST_REFRESH"), refreshFilter);
    } catch (Exception e) {
      // skip the test
      return;
    }

    // Then execute the test
    final File targetFile1 = new File(testFolder.getRoot(), SOURCE_FILENAME + ".page1.txt");

    LocalConverter.builder()
        .filterChain(
            new DefaultFilterChain(false, new PageCounterFilter(), new PagesSelectorFilter(1)))
        .build()
        .convert(SOURCE_FILE)
        .to(targetFile1)
        .execute();

    // Verify that the
    verify(refreshFilter, times(0))
        .doFilter(isA(OfficeContext.class), isA(XComponent.class), isA(FilterChain.class));
  }

  /**
   * Test that putting on the automatic insertion of refresh filter will execute it.
   *
   * @throws Exception if an error occurs.
   */
  @Test
  public void reset_WithEndsWithRefreshFilterOn_ShoudApplyRefreshFilter() throws Exception {

    // Replace the LAST_REFRESH singleton
    final RefreshFilter refreshFilter = mock(RefreshFilter.class);
    try {
      // TODO: Find a way to test under jdk12/13
      // This is not supported with jdk 12/13
      // https://bugs.openjdk.java.net/browse/JDK-8217225
      setFinalStatic(RefreshFilter.class.getDeclaredField("LAST_REFRESH"), refreshFilter);
    } catch (Exception e) {
      assumeNoException(e);
    }

    // Then execute the test
    final File targetFile1 = new File(testFolder.getRoot(), SOURCE_FILENAME + ".page1.txt");

    LocalConverter.builder()
        .filterChain(new DefaultFilterChain(new PageCounterFilter(), new PagesSelectorFilter(1)))
        .build()
        .convert(SOURCE_FILE)
        .to(targetFile1)
        .execute();

    // Verify that the
    verify(refreshFilter, times(1))
        .doFilter(isA(OfficeContext.class), isA(XComponent.class), isA(FilterChain.class));
  }

  private static void setFinalStatic(final Field field, final Object newValue) throws Exception {

    field.setAccessible(true);
    // This is not supported with jdk 12/13
    // https://bugs.openjdk.java.net/browse/JDK-8217225
    final Field modifiersField = Field.class.getDeclaredField("modifiers");
    modifiersField.setAccessible(true);
    modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
    field.set(null, newValue);
  }
}
