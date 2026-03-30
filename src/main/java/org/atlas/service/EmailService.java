package org.atlas.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EmailService {

	public static void main() {
		Resend resend = new Resend("re_PikfdQ27_EYtWQ4KGgrz55CA26TraVqwD");

		CreateEmailOptions params = CreateEmailOptions.builder()
			.from("andriy.chernenko008@gmail.com")
			.to("andrey.chernenko008@gmail.com")
			.subject("it works!")
			.html("<strong>hello world</strong>")
			.build();

		try {
			CreateEmailResponse data = resend.emails().send(params);
			System.out.println(data.getId());
		} catch (ResendException e) {
			e.printStackTrace();
		}
	}
}
