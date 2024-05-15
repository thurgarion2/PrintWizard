import java.util.List;
import java.util.Objects;

public class Messages {

    public static void main(String[] args) {
        List<Message> messages = List.of(new Message("hello"), new Message("my name is erwan"), new Message("bye"));

        try {
            for (Message m : messages) {
                send(m, new Client("anAddress"));
            }

            for (Message m : messages) {
                send(m, new Client("error"));
            }
        }catch (Exception e){

        }

        System.out.println("end of program");
    }


    public static void send(Message m, Client client){
        if(Objects.equals(client.address, "error")){
            throw new RuntimeException();
        }
    }

    public static class Client {
        public final String address;
        public Client(String address){
            this.address = address;
        }
    }
    public static class Message {
        public final String payload;
        public Message(String payload){
            this.payload = payload;
        }
    }
}
