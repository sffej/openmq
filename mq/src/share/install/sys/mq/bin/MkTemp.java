/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

import java.io.File;
import java.io.IOException;

/*
 * Java application to create temp files via java.io.File.createTempFile()
 *
 * java MkTemp [-prefix <tmpfile prefix>] [-suffix <tmpfile suffix>]
 * Defaults:
 *	<tmpfile prefix>	mqinstaller
 *	<tmpfile suffix>	null
 *
 */
public class MkTemp  {

    private String prefix	= "mqinstaller";
    private String suffix	= null;

    public MkTemp(String[] args)  {
	parseArgs(args);
    }

    public int doWork()  {
	int ret = 0;
	
	try  {
	    File tmpFile = File.createTempFile(prefix, suffix);
	    String tmpFilePath = tmpFile.getAbsolutePath();
	    System.out.println(tmpFilePath);
	} catch(IOException ioe)  {
	    System.err.println("Failed to create temp file: " + ioe);
	    ret = 1;
	}

	return (ret);
    }

    private void parseArgs(String[] args)  {
	for (int i = 0; i < args.length; ++i)  {
	    if (args[i].equals("-prefix"))  {
		if (++i >= args.length)  {
		    usage();
		}
		prefix = args[i];
	    } else if (args[i].equals("-suffix"))  {
		if (++i >= args.length)  {
		    usage();
		}
		suffix = args[i];
	    } else if (args[i].equals("-h"))  {
		usage();
	    } else  {
		usage();
	    }
	}

    }

    public void usage()  {
	usage(null);
    }

    public void usage(String msg)  {
	if (msg != null)  {
            System.out.println(msg);
	}

        System.out.println("Usage: java " 
		+ getClass().getName()
		+ " [-prefix <tmpfile prefix>] [-suffix <tmpfile suffix>]");

	System.exit(1);
    }
  
    public static void main(String[] args) {
	MkTemp mt = new MkTemp(args);
	int ret = mt.doWork();

	System.exit(ret);
    }

}