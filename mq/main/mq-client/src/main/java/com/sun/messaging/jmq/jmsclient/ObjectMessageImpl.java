/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2000-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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

/*
 * @(#)ObjectMessageImpl.java	1.19 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import java.io.*;
import javax.jms.*;

//import com.sun.messaging.AdministeredObject;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsclient.resources.ClientResources;
import com.sun.messaging.jmq.util.io.ClassFilter;

/** An ObjectMessage is used to send a message that contains a serializable
  * Java object. It inherits from <CODE>Message</CODE> and adds a body
  * containing a single Java reference. Only <CODE>Serializable</CODE> Java
  * objects can be used.
  *
  * <P>If a collection of Java objects must be sent, one of the collection
  * classes provided in JDK 1.2 can be used.
  *
  * <P>When a client receives an ObjectMessage, it is in read-only mode. If a
  * client attempts to write to the message at this point, a
  * MessageNotWriteableException is thrown. If <CODE>clearBody</CODE> is
  * called, the message can now be both read from and written to.
  *
  * @see         javax.jms.Session#createObjectMessage()
  * @see         javax.jms.Session#createObjectMessage(Serializable)
  * @see         javax.jms.BytesMessage
  * @see         javax.jms.MapMessage
  * @see         javax.jms.Message
  * @see         javax.jms.StreamMessage
  * @see         javax.jms.TextMessage
  */

public class ObjectMessageImpl extends MessageImpl implements ObjectMessage {

    private ByteArrayOutputStream byteArrayOutputStream = null;
    private ObjectOutputStream objectOutputStream = null;

    private ByteArrayInputStream byteArrayInputStream = null;
    private ObjectInputStream objectInputStream = null;

    //internal message body buffer.
    //do not return this buffer in getObject.  A copy should be made out
    //of this.
    private byte[] messageBody = null;

    //private byte[] defaultBytes = new byte [32];

    /**
     * default constructor
     */

    protected ObjectMessageImpl() throws JMSException {
        super();
        setPacketType ( PacketType.OBJECT_MESSAGE );
    }

    /**
     * set messageBody to null.
     * set message read mode = false.
     */
     public void clearBody() throws JMSException {
        messageBody = null;
        setMessageReadMode (false);
     }

     //serialize message body
     //This is called when producing messages.
    protected void
    setMessageBodyToPacket() throws JMSException {

        if (messageBody == null) {
            return;
        }
        try {
            setMessageBody ( messageBody );
        } catch (Exception e) {
            ExceptionHandler.handleException(e, ClientResources.X_MESSAGE_SERIALIZE);
        }
    }

    //deserialize message body
    //This is called after message is received in Session Reader.
    protected void
    getMessageBodyFromPacket() throws JMSException {

        try {
            messageBody = getMessageBody();
        } catch (Exception e) {
            ExceptionHandler.handleException(e, ClientResources.X_MESSAGE_DESERIALIZE);
        }
    }



    /** Set the serializable object containing this message's data.
      * It is important to note that an <CODE>ObjectMessage</CODE>
      * contains a snapshot of the object at the time <CODE>setObject()</CODE>
      * is called - subsequent modifications of the object will have no
      * affect on the <CODE>ObjectMessage</CODE> body.
      *
      * @param object the message's data
      *
      * @exception JMSException if JMS fails to  set object due to
      *                         some internal JMS error.
      * @exception MessageFormatException if object serialization fails
      * @exception MessageNotWriteableException if message in read-only mode.
      */

    public void
    setObject(Serializable object) throws JMSException {

        checkMessageAccess();

        try {

            byteArrayOutputStream = new ByteArrayOutputStream();
            objectOutputStream = new ObjectOutputStream ( byteArrayOutputStream );

            objectOutputStream.writeObject( object );
            objectOutputStream.flush();

            messageBody = byteArrayOutputStream.toByteArray();

            objectOutputStream.close();
            byteArrayOutputStream.close();

        } catch (Exception e) {
            if (e instanceof NotSerializableException) {
                //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_MESSAGE_SERIALIZE);

                String errorString =
                ExceptionHandler.getExceptionMessage(e, ClientResources.X_MESSAGE_SERIALIZE);

                MessageFormatException mfe =
                new com.sun.messaging.jms.MessageFormatException (errorString, ClientResources.X_MESSAGE_SERIALIZE);
                ExceptionHandler.handleException(e, mfe);
            }
            ExceptionHandler.handleException(e, ClientResources.X_CAUGHT_EXCEPTION);
        }

    }


    /** Get the serializable object containing this message's data. The
      * default value is null.
      *
      * @return the serializable object containing this message's data
      *
      * @exception JMSException if JMS fails to  get object due to
      *                         some internal JMS error.
      * @exception MessageFormatException if object deserialization fails
      */

    public Serializable
    getObject() throws JMSException {

        Serializable object = null;

        if ( messageBody == null ) {
            return null;
        }

        try {
            byteArrayInputStream = new ByteArrayInputStream ( messageBody );
            objectInputStream = new ObjectInputStreamWithContextLoader( byteArrayInputStream );

            object = (Serializable) objectInputStream.readObject();
        } catch ( Exception e ) {

            if ((e instanceof InvalidClassException)
                   || (e instanceof OptionalDataException)
                   || (e instanceof ClassNotFoundException)) {
                //String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_MESSAGE_DESERIALIZE);

                String errorString =
                ExceptionHandler.getExceptionMessage(e, ClientResources.X_MESSAGE_DESERIALIZE);

                MessageFormatException jmsex =
                new com.sun.messaging.jms.MessageFormatException(errorString, ClientResources.X_MESSAGE_DESERIALIZE);

                jmsex.setLinkedException(e);

                ExceptionHandler.throwJMSException(jmsex);
            }

            ExceptionHandler.handleException(e, ClientResources.X_CAUGHT_EXCEPTION);
        }

        return object;
    }

     public void dump (PrintStream ps) {
        ps.println ("------ ObjectMessageImpl dump ------");
        super.dump (ps);
    }

    public String toString() {
        return super.toString();
    }

    /**
     * This subclass of ObjectInputStream delegates class loading
     * to Thread.ContextClassLoader (if one set) if ObjectInputStream
     * class loading mechanism fails.
     */

    static class ObjectInputStreamWithContextLoader extends ObjectInputStream
    {

        public ObjectInputStreamWithContextLoader(InputStream in)
            throws IOException, StreamCorruptedException {

            super(in);
        }

        protected Class resolveClass(ObjectStreamClass classDesc)
            throws IOException, ClassNotFoundException
        {

            String className = classDesc.getName();
            if (className != null && !className.isEmpty() && ClassFilter.isBlackListed(className)) {
              throw new InvalidClassException("Unauthorized deserialization attempt", classDesc.getName());
            }

            try {
                return super.resolveClass(classDesc);
            } catch (ClassNotFoundException e) {


                //try Thread.ContextClassLoader
                ClassLoader ctxcl = null;
                try {
                    ctxcl = Thread.currentThread().getContextClassLoader();
                }
                catch (SecurityException se) {
                    throw new ClassNotFoundException(e.getMessage()+"; "+se.getMessage());
                }
                if (ctxcl == null)  {
                    throw e;
                }
                return Class.forName(classDesc.getName(), false, ctxcl);


            }

        }

    }
}
