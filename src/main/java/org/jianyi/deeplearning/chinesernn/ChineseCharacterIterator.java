package org.jianyi.deeplearning.chinesernn;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;

import org.ansj.util.MyStaticValue;
import org.deeplearning4j.datasets.iterator.DataSetIterator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.factory.Nd4j;

public class ChineseCharacterIterator implements DataSetIterator {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3655324423743466456L;
	private static final int MAX_SCAN_LENGTH = 200;
	private String[] validCharacters;
	private Map<String, Integer> charToIdxMap;
	private String[] fileCharacters;
	private int exampleLength;
	private int miniBatchSize;
	private int numExamplesToFetch;
	private int examplesSoFar = 0;
	private Random rng;
	private final int numCharacters;
	private final boolean alwaysStartAtNewLine;

	public ChineseCharacterIterator(String path, int miniBatchSize, int exampleSize, int numExamplesToFetch)
			throws IOException {
		this(path, Charset.defaultCharset(), miniBatchSize, exampleSize, numExamplesToFetch, getDefaultCharacterSet(),
				new Random(), true);
	}

	/**
	 * @param textFilePath
	 *            Path to text file to use for generating samples
	 * @param textFileEncoding
	 *            Encoding of the text file. Can try Charset.defaultCharset()
	 * @param miniBatchSize
	 *            Number of examples per mini-batch
	 * @param exampleLength
	 *            Number of characters in each input/output vector
	 * @param numExamplesToFetch
	 *            Total number of examples to fetch (must be multiple of
	 *            miniBatchSize). Used in hasNext() etc methods
	 * @param validCharacters
	 *            Character array of valid characters. Characters not present in
	 *            this array will be removed
	 * @param rng
	 *            Random number generator, for repeatability if required
	 * @param alwaysStartAtNewLine
	 *            if true, scan backwards until we find a new line character (up
	 *            to MAX_SCAN_LENGTH in case of no new line characters, to avoid
	 *            scanning entire file)
	 * @throws IOException
	 *             If text file cannot be loaded
	 */
	public ChineseCharacterIterator(String textFilePath, Charset textFileEncoding, int miniBatchSize, int exampleLength,
			int numExamplesToFetch, char[] validCharacters, Random rng, boolean alwaysStartAtNewLine)
					throws IOException {
		if (!new File(textFilePath).exists())
			throw new IOException("Could not access file (does not exist): " + textFilePath);
		if (numExamplesToFetch % miniBatchSize != 0)
			throw new IllegalArgumentException("numExamplesToFetch must be a multiple of miniBatchSize");
		if (miniBatchSize <= 0)
			throw new IllegalArgumentException("Invalid miniBatchSize (must be >0)");
		//this.validCharacters = validCharacters;
		this.exampleLength = exampleLength;
		this.miniBatchSize = miniBatchSize;
		this.numExamplesToFetch = numExamplesToFetch;
		this.rng = rng;
		this.alwaysStartAtNewLine = alwaysStartAtNewLine;
		
		List<String> lines = Files.readAllLines(new File(textFilePath).toPath(), textFileEncoding);
		

		// Store valid characters is a map for later use in vectorization
		charToIdxMap = new HashMap<>();
		/*for (int i = 0; i < validCharacters.length; i++)
			charToIdxMap.put(validCharacters[i], i);*/
		
		int currIdx = 0;
		int maxSize = lines.size();
		for (String s : lines) {
			
			List<String> thisLine = MyStaticValue.getCRFSplitWord().cut(s);
			maxSize += thisLine.size();
			//char[] thisLine = s.toCharArray();
			for (int i = 0; i < thisLine.size(); i++) {
				if (charToIdxMap.containsKey(thisLine.get(i))) {
					continue;
				}
				charToIdxMap.put(thisLine.get(i), currIdx);
				currIdx++;
			}
		}
		/*charToIdxMap.put('\n', currIdx++);
		charToIdxMap.put('\t', currIdx++);*/
		
		numCharacters = charToIdxMap.size();
		this.validCharacters = new String[numCharacters];
		Set<String> keyset = charToIdxMap.keySet();
		for (String c : keyset) {
			this.validCharacters[charToIdxMap.get(c)] = c;
		}

		// Load file and convert contents to a char[]
		//boolean newLineValid = charToIdxMap.containsKey('\n');
		//boolean newLineValid = true;
		
		 // add lines.size() to account for newline
									// characters at end of each line
				
		String[] characters = new String[maxSize];
		currIdx = 0;
		for (String s : lines) {
			//char[] thisLine = s.toCharArray();
			List<String> thisLine = MyStaticValue.getCRFSplitWord().cut(s);
			for (int i = 0; i < thisLine.size(); i++) {
				if (!charToIdxMap.containsKey(thisLine.get(i)))
					continue;
				characters[currIdx++] = thisLine.get(i);
			}
		}

		if (currIdx == characters.length) {
			fileCharacters = characters;
		} else {
			fileCharacters = Arrays.copyOfRange(characters, 0, currIdx);
		}
		if (exampleLength >= fileCharacters.length)
			throw new IllegalArgumentException("exampleLength=" + exampleLength
					+ " cannot exceed number of valid characters in file (" + fileCharacters.length + ")");

		int nRemoved = maxSize - fileCharacters.length;
		System.out.println("Loaded and converted file: " + fileCharacters.length + " valid characters of " + maxSize
				+ " total characters (" + nRemoved + " removed)");
	}

	/**
	 * A minimal character set, with a-z, A-Z, 0-9 and common punctuation etc
	 */
	public static char[] getMinimalCharacterSet() {
		List<Character> validChars = new LinkedList<>();
		/*for (char c = 'a'; c <= 'z'; c++)
			validChars.add(c);
		for (char c = 'A'; c <= 'Z'; c++)
			validChars.add(c);
		for (char c = '0'; c <= '9'; c++)
			validChars.add(c);*/
		/*for (char c = 19968; c <= 171941; c++)
			validChars.add(c);*/
		// char[] temp = {'!', '&', '(', ')', '?', '-', '\'', '"', ',', '.',
		// ':', ';', ' ', '\n', '\t'};
		//char[] temp = { '！', '&', '？', '-', '‘', '“', '，', '。', '：', '；', ' ', '\n', '\t' };
		char[] temp = { '\n', '\t' };
		for (char c : temp)
			validChars.add(c);
		char[] out = new char[validChars.size()];
		int i = 0;
		for (Character c : validChars)
			out[i++] = c;
		return out;
	}

	/** As per getMinimalCharacterSet(), but with a few extra characters */
	public static char[] getDefaultCharacterSet() {
		List<Character> validChars = new LinkedList<>();
		for (char c : getMinimalCharacterSet())
			validChars.add(c);
		char[] additionalChars = { '@', '#', '$', '%', '^', '*', '{', '}', '[', ']', '/', '+', '_', '\\', '|', '<',
				'>' };
		for (char c : additionalChars)
			validChars.add(c);
		char[] out = new char[validChars.size()];
		int i = 0;
		for (Character c : validChars)
			out[i++] = c;
		return out;
	}

	public String convertIndexToCharacter(int idx) {
		return validCharacters[idx];
	}

	public int convertCharacterToIndex(String c) {
		return charToIdxMap.get(c);
	}

	public String getRandomCharacter() {
		return validCharacters[(int) (rng.nextDouble() * validCharacters.length)];
	}

	public boolean hasNext() {
		return examplesSoFar + miniBatchSize <= numExamplesToFetch;
	}

	public DataSet next() {
		return next(miniBatchSize);
	}

	public DataSet next(int num) {
		if (examplesSoFar + num > numExamplesToFetch)
			throw new NoSuchElementException();
		// Allocate space:
		INDArray input = Nd4j.zeros(new int[] { num, numCharacters, exampleLength });
		INDArray labels = Nd4j.zeros(new int[] { num, numCharacters, exampleLength });

		int maxStartIdx = fileCharacters.length - exampleLength;

		// Randomly select a subset of the file. No attempt is made to avoid
		// overlapping subsets
		// of the file in the same minibatch
		for (int i = 0; i < num; i++) {
			int startIdx = (int) (rng.nextDouble() * maxStartIdx);
			int endIdx = startIdx + exampleLength;
			int scanLength = 0;
			if (alwaysStartAtNewLine) {
				while (startIdx >= 1 && fileCharacters[startIdx - 1] != "\n" && scanLength++ < MAX_SCAN_LENGTH) {
					startIdx--;
					endIdx--;
				}
			}

			int currCharIdx = charToIdxMap.get(fileCharacters[startIdx]); // Current
																			// input
			int c = 0;
			for (int j = startIdx + 1; j <= endIdx; j++, c++) {
				int nextCharIdx = charToIdxMap.get(fileCharacters[j]); // Next
																		// character
																		// to
																		// predict
				input.putScalar(new int[] { i, currCharIdx, c }, 1.0);
				labels.putScalar(new int[] { i, nextCharIdx, c }, 1.0);
				currCharIdx = nextCharIdx;
			}
		}

		examplesSoFar += num;
		return new DataSet(input, labels);
	}

	public int totalExamples() {
		return numExamplesToFetch;
	}

	public int inputColumns() {
		return numCharacters;
	}

	public int totalOutcomes() {
		return numCharacters;
	}

	public void reset() {
		examplesSoFar = 0;
	}

	public int batch() {
		return miniBatchSize;
	}

	public int cursor() {
		return examplesSoFar;
	}

	public int numExamples() {
		return numExamplesToFetch;
	}

	public void setPreProcessor(DataSetPreProcessor preProcessor) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

}