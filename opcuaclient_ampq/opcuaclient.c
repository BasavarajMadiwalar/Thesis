#include "open62541.h"
#include <signal.h>
#include "opcuaclient.h"

UA_Logger logger = UA_Log_Stdout;

char* readskill(char *url){

    /* Create a default configuration and modify the time out value to 5sec*/
    UA_ClientConfig config = UA_ClientConfig_default;
    config.timeout = 5000;

    /* Create a client instance*/
    UA_Client *client = UA_Client_new(config);


    /*Variant is used to hold a scalar value i.e int, string etc*/
    UA_Variant value;
    UA_Variant_init(&value);

    UA_StatusCode connStatus = UA_Client_connect(client, url);

    if(connStatus != UA_STATUSCODE_GOOD){
        UA_LOG_ERROR(logger, UA_LOGCATEGORY_CLIENT, "Not Connected. Retrying to connect");
        UA_sleep_ms(2000);
	UA_Client *client = UA_Client_new(config);
    }

    /* Important Find a way to delete the value before returning to cleanup memory.
     * otherwise on a long run, this might create an Issue*/

    /*Node Id of the variable holding skill value*/
    const UA_NodeId skillNodeId = UA_NODEID_STRING(1, "the.answer");
    UA_StatusCode readstatus = UA_Client_readValueAttribute(client, skillNodeId, &value);
    if(readstatus == UA_STATUSCODE_GOOD &&
        UA_Variant_hasScalarType(&value, &UA_TYPES[UA_TYPES_STRING])){
        UA_String *skill = (UA_String *)value.data;
        UA_Client_disconnect(client);
        UA_Client_delete(client);
        return skill->data;
    }
}
