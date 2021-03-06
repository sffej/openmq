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
 * @(#)TemporaryDestination.java	1.9 06/27/07
 */ 

package com.sun.messaging.jmq.jmsclient;

import java.util.Enumeration;
import java.util.Iterator;

import javax.jms.*;
import com.sun.messaging.AdministeredObject;
import com.sun.messaging.jmq.ClientConstants;

/**
 * TemporaryDestination encapsulates the functionality of a
 * Temporary Destination (TemporaryQueue or TemporaryTopic)
 *
 * A TemporaryDestination object is a unique object created
 * for the duration of the Connection in which it is created.
 *
 * It is a system defined Destination that can only be consumed
 * by the Connection that created it.
 */

public abstract class TemporaryDestination extends com.sun.messaging.Destination {

    protected ConnectionImpl connection = null;
    private boolean isDeleted = false;

	/**
     * Constructor used by Session.createTemporary()
     * name is set to temporary_destination://<destination_type>/clientID/localPort/sequence
     */
    protected TemporaryDestination(ConnectionImpl connection, String destination_type) throws JMSException {

        //XXX:BUG?:Name conflicts ??
        super(ClientConstants.TEMPORARY_DESTINATION_URI_PREFIX + destination_type +
                connection.getClientIDOrIPAddress() + "/" +
                connection.getProtocolHandler().getConnID() + "/" +
                connection.getTempDestSequence());
        this.connection = connection;

        //Temporary Destination needs to be created now on this connection by the broker
        connection.protocolHandler.createDestination(this);
        connection.addTempDest(this);
    }

    /**
     * Constructor for Message.getJMSReply().
     */
    protected TemporaryDestination(String name) throws JMSException {
        super(name);
    }

    /**
     * Constructor for Message.getJMSReply().
     */
    protected TemporaryDestination() throws JMSException {
        super();
    }

    /**
     * All Temporary Destinations are TEMPORARY
     */
    public boolean isTemporary() {
        return true;
    }

    /**
     * Delete this temporary destination. If there are still existing senders
     * or receivers still using it, then a JMSException will be thrown.
     *
     * @exception JMSException if JMS implementation fails to delete a
     *                         Temporary destination due to some internal error.
     */
    public void delete() throws JMSException {

        if (isDeleted) {
            return;
        }

        //Note: Not allowed to delete a Temp Dest unless it was created explicitly
        // i.e. Attempting to delete a 'JMSReplyTo' temporary destination is an error
        if (connection == null){
            //you can not delete this destination because you are not
            //owner/creator.
            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_DELETE_DESTINATION);
            throw new JMSException (errorString, AdministeredObject.cr.X_DELETE_DESTINATION);
        }
        
        if (connection.isClosed()) {
        	// the connection is closed which means its temporary destinations will already have been deleted
        	// nothing else to do
        	return;
        }

        //check if there are active consumer on this destination.
        this.checkConsumer();

        //decrease temp dest counter -- for connection recovery
        connection.decreaseTempDestCounter();
        connection.removeTempDest(this);
        //set flag
        isDeleted = true;
        //tell broker to delete me
        connection.getProtocolHandler().deleteDestination(this);
    }

    /**
     * Check that the specified connection created the specified temporary destination
     * and is therefore allowed to create a consumer on it
     * @param connection
     * @param dest
     * @throws JMSException
     */
    public static void checkTemporaryDestinationConsumerAllowed(ConnectionImpl connection, Destination dest) throws JMSException {

        String name  = null;
        String prefix = null;
        String conn_id = null;

        if (dest instanceof javax.jms.TemporaryQueue) {
            name = ((Queue)dest).getQueueName();
            prefix = ClientConstants.TEMPORARY_DESTINATION_URI_PREFIX + ClientConstants.TEMPORARY_QUEUE_URI_NAME;
        } else {
            if (dest instanceof javax.jms.TemporaryTopic) {
                 name = ((Topic)dest).getTopicName();
                 prefix = ClientConstants.TEMPORARY_DESTINATION_URI_PREFIX + ClientConstants.TEMPORARY_TOPIC_URI_NAME;
            }
        }
        if (name != null) {
            conn_id = connection.getClientIDOrIPAddress() + "/" +
                connection.getProtocolHandler().getConnID() + "/";
            if (!name.startsWith(prefix+conn_id)) {
            	// Temporary destination belongs to a closed connection or another connection
                String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_TEMP_DESTINATION_INVALID, name);
                throw new JMSException (errorString, AdministeredObject.cr.X_TEMP_DESTINATION_INVALID);
            }
        }
    }

    /**
     * Check if there are active consumer(s) for this temp destination.
     *
     * @throws JMSException If there are existing receivers still using it.
     */
    protected void checkConsumer() throws JMSException {

        //flag set to true if found consumer on this dest.
        boolean foundConsumer = false;

        //current dest name.
        String myName = this.getName();

        //get all consumers from this connection.
        Object[] consumers = connection.interestTable.toArray();

        //consumer var.
        Consumer consumer = null;

        //dest var. for the consumer.
        String destName = null;
        //dest. var. for the consumer.
        com.sun.messaging.Destination dest = null;

        /**
         * loop through all active consumers on this connection.
         */
        for ( int index = 0; index < consumers.length; index++) {
            //get consumer from array at index.
            consumer = (Consumer) consumers[index];
            //get dest for this consumer.
            dest =
            (com.sun.messaging.Destination) consumer.getDestination();
            //get dest name.
            destName = dest.getName();

            /**
             * compare if consumer is active on this destination.
             */
            if ( myName.equals(destName) ) {
                //found, set flag to true and break out of loop.
                foundConsumer = true;
                break;
            }
        }

        /**
         * if found consumer, throw JMSException.
         */
        if ( foundConsumer == true ) {
            String errorString = AdministeredObject.cr.getKString(AdministeredObject.cr.X_DELETE_DESTINATION);
            throw new JMSException (errorString, AdministeredObject.cr.X_DELETE_DESTINATION);
        }

    }

    public boolean checkSendCreateDest(Destination dest,ConnectionImpl con){
        
        try {
            checkTemporaryDestinationConsumerAllowed(con,dest);
        } catch (JMSException jmsEx){
            return false;
        }
        
        return true;
    }

    protected boolean isDeleted() {
        return isDeleted;
    }
    
}
