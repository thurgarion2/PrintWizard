import java.awt.*;
import java.awt.event.WindowAdapter;
import java.util.ArrayList;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;


public class Main {
    public static void main(String[] args) throws InterruptedException {

        List<Boid> boids = Stream
                .generate(World::createRandomBoid)
                .limit(10)
                .toList();
        int x = 0;
        while(++x<12){
            //Thread.sleep(50);
            boids = BoidLogic.tickWorld(boids, World.physics);
        }

    }

    static class Canvas extends Frame {
        static final int BOID_SIZE = 3;
        List<Boid> boids = Stream
                .generate(World::createRandomBoid)
                .limit(10)
                .toList();
        public Canvas()
        {
            setVisible(true);
            setSize(World.Physics.WIDTH, World.Physics.HEIGHT);
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e)
                {
                    System.exit(0);
                }
            });
        }
        public void paint(Graphics g)
        {
            if(boids==null){
                return;
            }
            boids = BoidLogic.tickWorld(boids, World.physics);
            g.setColor(Color.RED);
            for(Boid b : boids){
                Geometry.Vector2 pos = b.position();
                Geometry.Vector2 direction = b.velocity().normalized().scale(BOID_SIZE);
                Geometry.Vector2 leftPoint = pos.add(direction.orthogonal());
                Geometry.Vector2 upperPoint = pos.add(direction.scale(3));
                Geometry.Vector2 rightPoint = pos.minus(direction.orthogonal());
                int[] xPoints = new int[]{(int) leftPoint.x(), (int) upperPoint.x(), (int) rightPoint.x()};
                int[] yPoints = new int[]{(int) leftPoint.y(), (int) upperPoint.y(), (int) rightPoint.y()};
                g.fillPolygon(xPoints, yPoints, 3);
            }
        }
    }

    public record Boid(
            Geometry.Vector2 position,
            Geometry.Vector2 velocity) {

    }

    static public class BoidLogic {
        static final float EPSILON = 0.001F;

        static Stream<Boid> boidsWithinRadius(Boid thisBoid, Stream<Boid> boids, float radius) {
            Geometry.Vector2 pos = thisBoid.position();
            return boids
                    .filter(b -> pos.distanceTo(b.position()) < radius && !b.equals(thisBoid));
        }

        static Geometry.Vector2 avoidanceForce(Boid thisBoid, Stream<Boid> boidsWithinAvoidanceRadius) {
            Geometry.Vector2 pos = thisBoid.position();
            return boidsWithinAvoidanceRadius
                    .map(b -> {
                        Geometry.Vector2 bToThisBoid = pos.minus(b.position());
                        if (bToThisBoid.norm() < EPSILON) {
                            return Geometry.Vector2.zero();
                        } else {
                            return bToThisBoid.scale(1 / bToThisBoid.squaredNorm());
                        }
                    }).reduce(Geometry.Vector2.zero(), Geometry.Vector2::add);
        }

        static Geometry.Vector2 cohesionForce(Boid thisBoid, List<Boid> boidsWithinPerceptionRadius) {
            Geometry.Vector2 sum = Geometry.Vector2.zero();
            for(int i=0; i<boidsWithinPerceptionRadius.size(); ++i){
                sum = sum.add(boidsWithinPerceptionRadius.get(i).position);
            }
            Geometry.Vector2 center = sum.scale(1 / (float) boidsWithinPerceptionRadius.size());
            return center.minus(thisBoid.position());
        }

        static Geometry.Vector2 alignmentForce(Boid thisBoid, List<Boid> boidsWithinPerceptionRadius) {
            if (boidsWithinPerceptionRadius.isEmpty()) {
                return Geometry.Vector2.zero();
            } else {
                Geometry.Vector2 meanVelocity = boidsWithinPerceptionRadius.stream()
                        .map(Boid::velocity)
                        .reduce(Geometry.Vector2.zero(), Geometry.Vector2::add).scale(1 / (float) boidsWithinPerceptionRadius.size());
                return meanVelocity.minus(thisBoid.velocity());
            }
        }

        static Geometry.Vector2 containmentForce(Boid thisBoid, List<Boid> allBoids, int width, int height) {
            Geometry.Vector2 pos = thisBoid.position();
            if (pos.x() < 0 && pos.y() < 0) {
                return new Geometry.Vector2(1, 1);
            } else if (pos.x() < 0 && pos.y() > height) {
                return new Geometry.Vector2(1, -1);
            } else if (pos.x() > width && pos.y() < 0) {
                return new Geometry.Vector2(-1, 1);
            } else if (pos.x() > width && pos.y() > height) {
                return new Geometry.Vector2(-1, -1);
            } else if (pos.x() < 0) {
                return new Geometry.Vector2(1, 0);
            } else if (pos.x() > width) {
                return new Geometry.Vector2(-1, 0);
            } else if (pos.y() < 0) {
                return new Geometry.Vector2(0, 1);
            } else if (pos.y() > height) {
                return new Geometry.Vector2(0, -1);
            } else {
                return Geometry.Vector2.zero();
            }
        }

        static Geometry.Vector2 totalForce(Boid thisBoid, List<Boid> allBoids, World.Physics physics) {
            List<Boid> withinPerceptionRadius = boidsWithinRadius(thisBoid, allBoids.stream(), physics.perceptionRadius()).toList();
            Geometry.Vector2 cohere = cohesionForce(thisBoid, withinPerceptionRadius);
            Geometry.Vector2 align = alignmentForce(thisBoid, withinPerceptionRadius);
            Stream<Boid> withinAvoidanceRadius = boidsWithinRadius(thisBoid, withinPerceptionRadius.stream(), physics.avoidanceRadius());
            Geometry.Vector2 avoid = avoidanceForce(thisBoid, withinAvoidanceRadius);
            Geometry.Vector2 contain = containmentForce(thisBoid, allBoids, World.Physics.WIDTH, World.Physics.HEIGHT);

            return avoid.scale(physics.avoidanceWeight())
                    .add(cohere.scale(physics.cohesionWeight()))
                    .add(align.scale(physics.alignmentWeight()))
                    .add(contain.scale(physics.containmentWeight()));
        }

        static Boid tickBoid(Boid thisBoid, List<Boid> allBoids, World.Physics physics) {
            Geometry.Vector2 acceleration = totalForce(thisBoid, allBoids, physics);
            Geometry.Vector2 velocity = thisBoid.velocity().add(acceleration);
            if (velocity.norm() > physics.maximumSpeed()) {
                velocity = velocity.normalized().scale(physics.maximumSpeed());
            }

            if (velocity.norm() < physics.minimumSpeed()) {
                velocity = velocity.normalized().scale(physics.minimumSpeed());
            }

            return new Boid(thisBoid.position().add(thisBoid.velocity()), velocity);
        }

        public static List<Boid> tickWorld(List<Boid> allBoids, World.Physics physics) {
            List<Boid> newBoids = new ArrayList<>(10);
            for(int i=0; i<allBoids.size(); ++i){
                newBoids.add(tickBoid(allBoids.get(i), allBoids, physics));
            }
            return new ArrayList<>(newBoids);
        }
    }

    static public class World {
        static Random rand = new Random(4);
        static World.Physics physics = new World.Physics(
                2f,
                8f,
                80f,
                15f,
                1f,
                0.001f,
                0.027f,
                0.5f);


        static Boid createRandomBoid() {
            float x = randomFromRange((float) Physics.WIDTH /3, (float) (Physics.WIDTH * 2) /3);
            float y = randomFromRange((float) Physics.HEIGHT /3, (float) Physics.HEIGHT*2/3);
            float rotation = randomFromRange(0, (float) (2 * Math.PI));
            float initialSpeed = randomFromRange(physics.maximumSpeed(), physics.maximumSpeed());
            Geometry.Vector2 initialVelocity = Geometry.Vector2.UnitUp().rotate(rotation).scale(initialSpeed);
            return new Boid(new Geometry.Vector2(x, y), initialVelocity);
        }

        static float randomFromRange(float start, float end) {
            return rand.nextFloat() * (end - start) + start;
        }

        public record Physics(
                float minimumSpeed,
                float maximumSpeed,
                float perceptionRadius,
                float avoidanceRadius,
                float avoidanceWeight,
                float cohesionWeight,
                float alignmentWeight,
                float containmentWeight
        ) {
            public static final int WIDTH = 1000;
            public static final int HEIGHT = 700;
        }
    }

    static  public class Geometry {

        public record Vector2(float x, float y){

            public static Geometry.Vector2 zero(){return new Geometry.Vector2(0, 0);}
            public static Geometry.Vector2 UnitUp(){
                return new Geometry.Vector2(0, -1);
            }

            public Geometry.Vector2 rotate(float radians) {
                return new Geometry.Vector2(
                        (float) (Math.cos(radians)*x - Math.sin(radians)*y),
                        (float) (Math.sin(radians) * x + Math.cos(radians) * y)
                );
            }

            public float squaredNorm(){
                return this.x*this.x+this.y*this.y;
            }

            public float norm(){
                return (float) Math.sqrt(squaredNorm());
            }

            public Geometry.Vector2 normalized(){
                if(norm()==0){
                    return zero();
                }else {
                    return this.scale(1/norm());
                }
            }

            public Geometry.Vector2 orthogonal(){
                return new Geometry.Vector2(-this.y, this.x);
            }



            public float squaredDistanceTo(Geometry.Vector2 that){
                float dx = this.x - that.x;
                float dy = this.y - that.y;
                return dx*dx+dy*dy;
            }

            public Geometry.Vector2 minus(Geometry.Vector2 that){
                return new Geometry.Vector2(this.x-that.x, this.y-that.y);
            }

            public Geometry.Vector2 add(Geometry.Vector2 that){
                return new Geometry.Vector2(this.x+that.x, this.y+that.y);
            }

            public float distanceTo(Geometry.Vector2 that){
                return (float) Math.sqrt(squaredDistanceTo(that));
            }

            public Geometry.Vector2 scale(float scale){
                return new Geometry.Vector2(scale*x, scale*y);
            }
        }
    }
}