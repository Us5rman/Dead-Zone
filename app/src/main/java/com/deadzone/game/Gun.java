package com.deadzone.game;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

import static com.deadzone.game.Node.box;
import static com.deadzone.game.Node.cyl;

final class Gun {
    final Node gun = new Node();
    final Node slide = new Node();
    final Node ham = new Node();
    final Node grip = new Node();
    final Node mag = new Node();
    final Node flash = new Node();

    final Mat mSt = Mat.color(0xa4aab4).emissive(0x15171a, 1);
    final Mat mFlash = Mat.color(0xffd070).alpha(.9);

    Gun() {
        Mat slideM = Mat.textured(Tex.tile((c, w, h) -> {
            Tex.noise(c, 52, 58, 70, 14);
            for (int i = 0; i < 14; i++) {
                Tex.rect(c, "#9097a2", Tex.R.nextInt(64), Tex.R.nextInt(64), 3 + Tex.R.nextInt(4), 1);
            }
        }), 1).emissive(0x0b0c10, 1);
        Mat frameM = Mat.textured(Tex.tile((c, w, h) -> {
            Tex.noise(c, 70, 72, 76, 20);
            for (int i = 0; i < 30; i++) Tex.rect(c, "#2c2e32", Tex.R.nextInt(64), Tex.R.nextInt(64), 1, 1);
        }), 1).emissive(0x0b0c10, 1);
        Mat gripM = Mat.textured(Tex.tile((c, w, h) -> {
            Tex.noise(c, 98, 56, 30, 22);
            Paint p = new Paint();
            p.setAntiAlias(false);
            p.setStrokeWidth(1);
            p.setColor(Color.parseColor("#2a1408"));
            for (int i = -64; i < 128; i += 7) {
                c.drawLine(i, 0, i + 64, 64, p);
                c.drawLine(i + 64, 0, i, 64, p);
            }
        }), 1).emissive(0x1a0d06, 1);
        Mat dk = Mat.color(0x0b0b0e).emissive(0x040405, 1);
        Mat dot = Mat.basic(0xf2f2e8);

        slide.parts.add(box(.054, .052, .40, 0, .034, -.025, slideM));
        slide.parts.add(box(.018, .004, .40, 0, .062, -.025, dk));
        slide.parts.add(box(.046, .042, .012, 0, .032, -.231, slideM));
        slide.parts.add(box(.004, .022, .07, .0275, .046, .03, dk));
        slide.parts.add(box(.044, .004, .07, 0, .0615, .03, dk));
        slide.parts.add(cyl(.018, .011, 0, .034, -.2305, mSt, 10));
        for (int i = 0; i < 6; i++) slide.parts.add(box(.0574, .038, .006, 0, .034, .087 + i * .014, dk));
        for (int i = 0; i < 3; i++) slide.parts.add(box(.0574, .038, .006, 0, .034, -.185 + i * .014, dk));
        slide.parts.add(box(.032, .006, .014, 0, .0655, .158, dk));
        slide.parts.add(box(.011, .012, .014, -.0105, .0715, .158, dk));
        slide.parts.add(box(.011, .012, .014, .0105, .0715, .158, dk));
        slide.parts.add(box(.004, .004, .002, -.0105, .0725, .1655, dot));
        slide.parts.add(box(.004, .004, .002, .0105, .0725, .1655, dot));
        slide.parts.add(box(.008, .014, .012, 0, .068, -.2, dk));
        slide.parts.add(box(.003, .003, .002, 0, .0715, -.1935, dot));

        Mat eng = Mat.textured(new Tex(128, 16, false, (c, w, h) -> {
            Paint p = new Paint();
            p.setAntiAlias(false);
            p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            p.setTextSize(9);
            p.setColor(Color.parseColor("#c4c8d0"));
            c.drawText("COLT M1911 .45 ACP", 6, h / 2f + 4, p);
        }), 1);
        eng.unlit = true;
        eng.blend = true;
        eng.depthWrite = false;
        eng.offset = true;
        Node.Part engL = new Node.Part(Geo.plane(.13, .016), eng, -.0277, .034, -.075);
        engL.ry = -Math.PI / 2;
        Node.Part engR = new Node.Part(Geo.plane(.13, .016), eng, .0277, .034, -.075);
        engR.ry = Math.PI / 2;
        slide.parts.add(engL);
        slide.parts.add(engR);

        gun.parts.add(cyl(.0115, .10, 0, .034, -.21, mSt, 10));
        gun.parts.add(cyl(.0075, .002, 0, .034, -.2605, dk, 10));
        gun.parts.add(box(.05, .038, .33, 0, -.011, -.035, frameM));
        gun.parts.add(box(.052, .006, .12, 0, -.026, -.135, frameM));
        gun.parts.add(box(.012, .029, .008, 0, -.0445, -.045, frameM));
        gun.parts.add(box(.012, .006, .108, 0, -.056, .009, frameM));
        gun.parts.add(box(.011, .022, .008, 0, -.043, .064, frameM));
        Node.Part trig = box(.007, .024, .01, 0, -.041, -.006, mSt);
        trig.rx = .25;
        gun.parts.add(trig);
        gun.parts.add(box(.006, .012, .026, -.0275, .004, .092, mSt));
        gun.parts.add(box(.007, .008, .03, -.0275, .0, .006, mSt));
        gun.parts.add(box(.007, .012, .012, -.0275, -.036, .052, mSt));

        ham.at(0, .05, .172);
        ham.parts.add(box(.012, .022, .012, 0, .006, .004, mSt));
        ham.parts.add(box(.016, .006, .012, 0, .018, .01, mSt));

        grip.at(0, -.025, .13);
        grip.rx = -.2;
        grip.parts.add(box(.046, .13, .075, 0, -.065, -.0375, frameM));
        grip.parts.add(box(.007, .12, .066, -.0265, -.066, -.04, gripM));
        grip.parts.add(box(.007, .12, .066, .0265, -.066, -.04, gripM));
        grip.parts.add(box(.044, .016, .044, 0, .006, .014, frameM));
        grip.parts.add(box(.04, .1, .012, 0, -.07, .006, dk));
        grip.parts.add(box(.05, .008, .082, 0, -.134, -.037, dk));
        grip.parts.add(box(.002, .007, .007, -.0305, -.03, -.045, mSt));
        grip.parts.add(box(.002, .007, .007, .0305, -.03, -.045, mSt));
        grip.parts.add(box(.002, .007, .007, -.0305, -.095, -.045, mSt));
        grip.parts.add(box(.002, .007, .007, .0305, -.095, -.045, mSt));

        mag.at(0, -.14, -.037);
        mag.parts.add(box(.042, .012, .074, 0, -.004, 0, dk));
        mag.parts.add(box(.036, .1, .06, 0, .058, 0, mSt));
        grip.kids.add(mag);

        flash.at(0, .034, -.3);
        mFlash.additive = false;
        Node.Part f1 = new Node.Part(Geo.plane(.1, .1), mFlash, 0, 0, 0);
        Node.Part f2 = new Node.Part(Geo.plane(.1, .1), mFlash, 0, 0, 0);
        f2.rz = Math.PI / 4;
        Node.Part f3 = new Node.Part(Geo.plane(.1, .1), mFlash, 0, 0, 0);
        f3.ry = Math.PI / 2;
        flash.parts.add(f1);
        flash.parts.add(f2);
        flash.parts.add(f3);
        flash.visible = false;

        gun.kids.add(slide);
        gun.kids.add(ham);
        gun.kids.add(grip);
        gun.kids.add(flash);
        gun.s = 1.1;
        gun.ry = .07;
        gun.at(.22, -.2, -.55);
    }
}
