/**
 * Copyright 2012 Tobias Gierke <tobias.gierke@code-sourcery.de>
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
package de.codesourcery.entropy;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * Command-line tool that creates an image from the entropy distribution
 * inside arbitrary files (Swing UI). 
 * 
 * <p>This tool scans the file using a sliding window and
 * for each window calculates the metric entropy. The
 * generated image uses a color gradient from black (entropy = 0)
 * to bright red (entropy: 1) to visualize the entropy level.</p>
 *  
 * <p>Command-Line arguments are:
 * <pre>
 * [-v|--verbose] [--help|-help] [--window-size &lt;size in bytes&gt;] [--window-stride &lt;stride in bytes&gt;] &lt;filename&gt;
 * </pre>
 * </p>
 * @author tobias.gierke@code-sourcery.de
 */
public class Main
{
    private static final int DEFAULT_WINDOW_SIZE = 32;
    private static final int DEFAULT_WINDOW_STRIDE = 1;

    private static final int DEFAULT_IMAGE_WIDTH = 800;
    private static final int DEFAULT_IMAGE_HEIGHT = 600;
    
    private static final boolean SCALE = true;
    
    private static final double LOG10_OF_2 = Math.log10( 2 );

    private MyPanel panel;
    private final boolean verbose;
    private final int windowSize;
    private final int windowStride;

    protected class MyPanel extends JPanel
    {
        private final Color[] colors = new Color[256];

        private double[] metricEntropy;
        
        private double minValue;
        private double maxValue;        

        public MyPanel(Dimension panelSize, double[] metricEntropy) 
        {
            final int r = 255;
            final int g = 0;
            final int b = 0;

            double currentFactor = 0;
            double increment = 1 / (double) colors.length;
            for ( int i = 0 ; i < 256 ; i++ , currentFactor+=increment ) 
            {
                colors[i] = new Color( (int) ( r * currentFactor) , (int) ( g * currentFactor) , (int) ( b * currentFactor) );
            }
            setPreferredSize( panelSize );
            setData( metricEntropy );
        }

        public void setData(double[] metricEntropy) 
        {
            synchronized( this ) 
            {
                this.metricEntropy = metricEntropy;
                
                double min=Long.MAX_VALUE;
                double max=Long.MIN_VALUE;
                
                double sum = 0;
                
                for ( double v : metricEntropy ) 
                {
                    sum += v;
                    if ( v < min ) {
                        min = v;
                    }
                    if ( v > max ) {
                        max = v;
                    }
                }
                sum = sum / metricEntropy.length;
                
                log("Average metric entropy: "+sum);
                
                if ( metricEntropy.length != 0 ) {
                    minValue = min;
                    maxValue = max;
                } else {
                    minValue = maxValue = 0;
                }
            }
        }
        
        @Override
        public void paint(Graphics g)
        {
            long time =-System.currentTimeMillis();
            try {
                internalPaint(g);
            } 
            finally {
                time += System.currentTimeMillis();
                log("Painted "+metricEntropy.length+" elements in "+time+" ms");
            }
        }
        
        private void internalPaint(Graphics g)
        {
            double currentX = 0;
            double currentY = 0;            

            synchronized (this) 
            {
                final int width = getWidth();
                final int height = getHeight();

                final double[] data = this.metricEntropy;
                int len= data.length;

                if ( len == 0 ) {
                    g.setColor( colors[ 0 ] );
                    g.fillRect( 0 , 0, width , height );
                    return;
                }
                
                double pixelsPerSlidingWindow = (width*height) / (double) len;
                
                final double factor;
                if ( SCALE && (maxValue-minValue) != 0 ) {
                    factor = 1 / ( maxValue - minValue );
                } else {
                    factor = 1;
                }
                
                log("min="+minValue+" / max="+maxValue+" / width="+width+" / height="+height+" / pixels = "+pixelsPerSlidingWindow+" / factor: "+factor);

                for ( int i = 0 ; i < len ; i++ ) 
                {
                    final double scaled = (data[i] - minValue ) * factor;
                    g.setColor( colors[ (int) ( scaled * 255) ] );

                    // draw pixels
                    double pixelsLeft = pixelsPerSlidingWindow;
                    while ( true ) 
                    {
                        if ( pixelsLeft < ( width - currentX ) ) {
                            g.drawLine( (int) currentX,(int) currentY , (int) (currentX+pixelsLeft), (int) currentY );
                            currentX += pixelsLeft;                               
                            break;
                        } 

                        g.drawLine( (int) currentX, (int) currentY , width , (int) currentY++ );
                        pixelsLeft -= ( width - currentX );
                        currentX = 0;
                    }
                }
            }
        }
    }

    public Main(int windowSize, int windowStride,boolean verbose) 
    {
		this.windowSize = windowSize;
		this.windowStride = windowStride;
		this.verbose = verbose;
		log("Window size: "+windowSize+" bytes");
		log("Window stride: "+windowStride+" bytes");
	}

    protected void log(String message) {
    	if ( verbose ) {
    		System.out.println( message );
    	}
    }
    
	public static void main(String[] args) throws Exception
    {
    	int size = DEFAULT_WINDOW_SIZE;
    	int stride = DEFAULT_WINDOW_STRIDE;
    	File file = null;
    	boolean verboseMode = false;
    	
    	for (int i = 0; i < args.length; i++) {
			String arg= args[i];
			if ( "--window-size".equals( arg ) ) {
				size = Integer.parseInt( args[i+1] );
				i++;
			} else if ("--window-stride".equals( arg ) ) {
				stride = Integer.parseInt( args[ i+1 ] );
				i++;
			} 
			else if ( "-v".equals( arg ) || "--verbose".equals(arg ) ) 
			{
				verboseMode = true;
			}
			else if ( "-help".equals( arg ) || "--help".equals(arg ) ) 
			{
				System.out.println("\n\nUsage:\n\n"+
			          "[-v|--verbose] [--help|-help] [--window-size <size in bytes>] [--window-stride <size in bytes>] <file>\n");
				System.exit(1);
			} else {
				file = new File( arg );
			}
		}
        new Main(size,stride,verboseMode).run( file );
    }

    private void run(final File file) throws IOException, InvocationTargetException, InterruptedException
    {
    	final double[] data;
    	if ( file != null && file.exists() && file.isFile() && file.canRead() ) 
    	{
    		log("Analyzing "+file.getAbsolutePath());
    		 data = calculateMetricEntropy(new FileInputStream( file ) );
    	} else {
    		data = new double[0];
    	}
        panel = new MyPanel(new Dimension(DEFAULT_IMAGE_WIDTH,DEFAULT_IMAGE_HEIGHT) , data);

        final AtomicReference<File> lastFile = new AtomicReference<>(file);

        final JFrame[] frame = {null};

        SwingUtilities.invokeAndWait( new Runnable() {

            @Override
            public void run()
            {
                frame[0] = new JFrame();        
                frame[0] .setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame[0] .getContentPane().add( panel );
                frame[0] .pack();
                frame[0] .setVisible(true);

                analyzeFile( file , windowSize , windowStride );
            }
        });

        while( true ) 
        {
            SwingUtilities.invokeAndWait( new Runnable() 
            {
                public void run() 
                {
                    final JFileChooser chooser;
                    if ( lastFile.get() == null ) {
                        chooser = new JFileChooser();
                    } else {
                        chooser = new JFileChooser(lastFile.get().getParentFile());
                    }
                    chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                    chooser.setLocation( 400, 300 );

                    if ( chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION  ) 
                    {
                        System.exit(0);
                    }

                    if ( chooser.getSelectedFile().isFile() ) 
                    {
                        final File selected = chooser.getSelectedFile(); 
                        lastFile.set( selected );
                        analyzeFile( selected , windowSize, windowStride );
                    }
                }
            });

        }
    }

    private void analyzeFile(final File selected,final int windowSize,final int windowStride) {

        final SwingWorker<double[],String> worker = new SwingWorker<double[],String>() {

            private double[] metricEntropy=new double[0];

            @Override
            protected double[] doInBackground() throws Exception
            {
                log("Analyzing "+selected.getAbsolutePath());
                this.metricEntropy = calculateMetricEntropy(new FileInputStream( selected ));
                return null;
            }

            @Override
            protected void done()
            {
                panel.setData( metricEntropy );
                panel.repaint();
            }
        };

        // execute swing worker
        worker.execute();
    }

    private double[] calculateMetricEntropy(final InputStream inputStream) throws IOException 
    {
        final BufferedInputStream buffered = new BufferedInputStream(inputStream);
        
        long time1 = -System.currentTimeMillis();           
        double[] array = new double[0];
        try 
        {
            final SlidingWindow window = new SlidingWindow(windowSize,windowStride ) {

                @Override
                protected int readByte() throws IOException
                {
                    return buffered.read();
                }
            };

            final ArrayList<Double> result = new ArrayList<>(1000);
            while(true) 
            {
                result.add( Double.valueOf( calculateMetricEntropy( window ) ) );

                if ( window.eof() ) {
                    break;
                }
                window.advance();
            } 

            array = new double[ result.size() ];
            int i = 0;
            for ( Double d : result ) {
                array[i++] = d.doubleValue();
            }
            return array;
        } 
        finally {
            time1 += System.currentTimeMillis();
            log("Time: "+time1+" ("+array.length+" elements)");
        }        
    }

    protected double calculateMetricEntropy(SlidingWindow window) 
    {
        final int[] occuranceCounts = new int[256];

        final byte[] data = window.getBytes();

        final int len = data.length;
        final double windowSize = len;

        for ( int i = 0 ; i < len ; i++ ) {
            occuranceCounts[ ( data[i] & 0xff ) ]++;
        }

        // calculate Shannon entropy
        double sum=0;
        for ( int i = 0 ; i < 256 ; i++ ) 
        {
            final int count = occuranceCounts[i];
            if ( count != 0 ) {
                double probability = count / windowSize;
                sum += ( probability * log2( probability ) );
            }
        }
        sum = -sum;
        return sum / windowSize;
    }

    protected static final double log2(double value) {
        return Math.log10( value ) / LOG10_OF_2;
    }

    protected static double byteArrayToDouble(byte[] entropy,int offset) {
        int i = offset;
        long value = (entropy[i++] & 0xff);
        value = (value << 8 ) | ( entropy[i++] & 0xff);
        value = (value << 8 ) | ( entropy[i++] & 0xff);
        value = (value << 8 ) | ( entropy[i++] & 0xff);
        value = (value << 8 ) | ( entropy[i++] & 0xff);
        value = (value << 8 ) | ( entropy[i++] & 0xff);
        value = (value << 8 ) | ( entropy[i++] & 0xff);
        value = (value << 8 ) | ( entropy[i++] & 0xff);
        return Double.longBitsToDouble( value );
    }

    protected static void doubleToByteArray(double value, byte[] entropy) 
    {
        int i = 7;
        long longValue = Double.doubleToLongBits( value );
        entropy[i--] = (byte) (longValue & 0xff);
        longValue = longValue >> 8;
        entropy[i--] = (byte) (longValue & 0xff);
        longValue = longValue >> 8;
        entropy[i--] = (byte) (longValue & 0xff);
        longValue = longValue >> 8;
        entropy[i--] = (byte) (longValue & 0xff);
        longValue = longValue >> 8;
        entropy[i--] = (byte) (longValue & 0xff);
        longValue = longValue >> 8;
        entropy[i--] = (byte) (longValue & 0xff);
        longValue = longValue >> 8;
        entropy[i--] = (byte) (longValue & 0xff);
        longValue = longValue >> 8;
        entropy[i--] = (byte) (longValue & 0xff);
    }    

    protected static abstract class SlidingWindow {

        private final int size;
        private final int stride;

        private int bytesInBuffer;

        private boolean atEOF = false;
        private final byte[] buffer;

        public SlidingWindow(int size,int stride) throws IOException {
            this.size = size;
            if ( stride > size || stride <= 0) {
                throw new IllegalArgumentException("stride must be greater than zero and less than/equal to size");
            }
            this.stride = stride;
            this.buffer = new byte[size];
            fill();
        }

        private void fill() throws IOException 
        {
            int read = 0;
            int data;
            while ( ( data = readByte() ) != -1 ) {
                buffer[read++] = (byte) data;
                if ( read == size ) {
                    break;
                }
            }
            this.bytesInBuffer = read;
            if ( data == -1 ) {
                atEOF = true;
            }
        }

        protected abstract int readByte() throws IOException;

        public byte[] getBytes() 
        {
            if ( bytesInBuffer == size ) {
                return buffer;
            }
            return Arrays.copyOfRange( buffer , 0, bytesInBuffer );
        }

        public boolean eof()
        {
            return atEOF;
        }

        public boolean advance() throws IOException 
        {
            if ( atEOF ) {
                throw new IllegalStateException("Already at EOF");
            }

            // shift buffer contents
            int src = stride;
            int dst = 0;
            final int bytesToMove = bytesInBuffer - stride;
            for ( int i = 0 ; i < bytesToMove ; i++ ) {
                buffer[dst++] = buffer[src++];
            }  

            // fetch more data
            int value = 0;
            int offset = size-stride;
            while ( ( value = readByte() ) != -1  ) {
                buffer[ offset ++ ] = (byte) value;
                if ( offset >= size ) {
                    break;
                }
            }

            final int count = offset - (size-stride);
            if ( value == -1 ) 
            {
                atEOF = true;
                if ( count == 0 ) {
                    return false;
                }
            }

            bytesInBuffer = bytesInBuffer-stride+count;

            return true;
        }
    }
}